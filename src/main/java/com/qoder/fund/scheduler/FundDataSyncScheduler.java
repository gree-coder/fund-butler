package com.qoder.fund.scheduler;

import com.qoder.fund.datasource.EastMoneyDataSource;
import com.qoder.fund.datasource.FundDataAggregator;
import com.qoder.fund.dto.EstimateSourceDTO;
import com.qoder.fund.dto.FundDetailDTO;
import com.qoder.fund.entity.EstimatePrediction;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.entity.FundNav;
import com.qoder.fund.entity.Position;
import com.qoder.fund.entity.Watchlist;
import com.qoder.fund.mapper.EstimatePredictionMapper;
import com.qoder.fund.mapper.FundMapper;
import com.qoder.fund.mapper.FundNavMapper;
import com.qoder.fund.mapper.PositionMapper;
import com.qoder.fund.mapper.WatchlistMapper;
import com.qoder.fund.service.TradingCalendarService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class FundDataSyncScheduler {

    private final FundMapper fundMapper;
    private final FundNavMapper fundNavMapper;
    private final PositionMapper positionMapper;
    private final WatchlistMapper watchlistMapper;
    private final EstimatePredictionMapper estimatePredictionMapper;
    private final EastMoneyDataSource eastMoneyDataSource;
    private final FundDataAggregator fundDataAggregator;
    private final TradingCalendarService tradingCalendarService;

    /**
     * 应用启动时自动补偿缺失的数据
     * 1. 补偿缺失的净值数据（最后记录日期 → 昨天）
     * 2. 回填已有快照但未评估的预测记录
     * 3. 重仓股超过7天未更新则刷新
     */
    @PostConstruct
    public void compensateOnStartup() {
        log.info("=== 启动数据补偿检查 ===");
        try {
            compensateMissingNav();
        } catch (Exception e) {
            log.warn("净值补偿失败", e);
        }
        try {
            compensateUnevaluatedPredictions();
        } catch (Exception e) {
            log.warn("预测评估补偿失败", e);
        }
        try {
            compensateStaleHoldings();
        } catch (Exception e) {
            log.warn("重仓股补偿失败", e);
        }
        log.info("=== 启动数据补偿完成 ===");
    }

    /**
     * 补偿缺失的净值数据：对每个基金，从最后净值日期的下一个交易日补到昨天
     */
    private void compensateMissingNav() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        while (!tradingCalendarService.isTradingDay(yesterday)) {
            yesterday = yesterday.minusDays(1);
        }

        List<Fund> funds = fundMapper.selectList(null);
        if (funds.isEmpty()) {
            log.info("净值补偿: 无基金数据，跳过");
            return;
        }

        int totalCompensated = 0;
        for (Fund fund : funds) {
            try {
                List<FundNav> latestNavs = fundNavMapper.selectList(
                        new QueryWrapper<FundNav>()
                                .eq("fund_code", fund.getCode())
                                .orderByDesc("nav_date")
                                .last("LIMIT 1")
                );

                LocalDate lastDate;
                if (latestNavs.isEmpty()) {
                    lastDate = yesterday.minusDays(7);
                } else {
                    lastDate = latestNavs.get(0).getNavDate();
                }

                if (!lastDate.isBefore(yesterday)) continue;

                LocalDate startDate = lastDate.plusDays(1);
                String start = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                String end = yesterday.format(DateTimeFormatter.ISO_LOCAL_DATE);

                List<Map<String, Object>> navList = eastMoneyDataSource.getNavHistory(
                        fund.getCode(), start, end);

                for (Map<String, Object> navData : navList) {
                    saveNav(fund.getCode(), navData);
                    totalCompensated++;
                }

                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("净值补偿失败: fund={}", fund.getCode(), e);
            }
        }
        log.info("净值补偿完成, 补充 {} 条记录", totalCompensated);
    }

    /**
     * 回填已有快照但未评估的预测记录（应用在14:50运行了但20:00没运行的情况）
     */
    private void compensateUnevaluatedPredictions() {
        List<EstimatePrediction> pending = estimatePredictionMapper.selectList(
                new QueryWrapper<EstimatePrediction>()
                        .isNull("actual_return")
                        .isNotNull("predicted_return")
        );

        if (pending.isEmpty()) {
            log.info("预测评估补偿: 无待回填记录");
            return;
        }

        int evaluated = 0;
        for (EstimatePrediction prediction : pending) {
            try {
                List<FundNav> navs = fundNavMapper.selectList(
                        new QueryWrapper<FundNav>()
                                .eq("fund_code", prediction.getFundCode())
                                .eq("nav_date", prediction.getPredictDate())
                                .last("LIMIT 1")
                );
                if (navs.isEmpty()) continue;

                FundNav actualNav = navs.get(0);
                prediction.setActualNav(actualNav.getNav());
                prediction.setActualReturn(actualNav.getDailyReturn());

                if (prediction.getPredictedReturn() != null && actualNav.getDailyReturn() != null) {
                    prediction.setReturnError(
                            prediction.getPredictedReturn().subtract(actualNav.getDailyReturn())
                                    .setScale(4, RoundingMode.HALF_UP)
                    );
                }

                estimatePredictionMapper.updateById(prediction);
                evaluated++;
            } catch (Exception e) {
                log.warn("预测评估补偿失败: id={}", prediction.getId(), e);
            }
        }
        log.info("预测评估补偿完成, 回填 {} 条记录", evaluated);
    }

    /**
     * 检查重仓股数据是否陈旧，超过7天未更新则触发刷新
     */
    private void compensateStaleHoldings() {
        Set<String> fundCodes = getTrackedFundCodes();
        if (fundCodes.isEmpty()) {
            log.info("重仓股补偿: 无用户关注的基金");
            return;
        }

        LocalDateTime threshold = LocalDateTime.now().minusDays(7);
        int refreshed = 0;

        for (String fundCode : fundCodes) {
            try {
                Fund fund = fundMapper.selectById(fundCode);
                if (fund == null) continue;

                if (fund.getUpdatedAt() != null && fund.getUpdatedAt().isAfter(threshold)) {
                    continue;
                }

                FundDetailDTO detail = eastMoneyDataSource.getFundDetail(fundCode);
                if (detail == null) continue;

                fund.setTopHoldings(detail.getTopHoldings());
                fund.setIndustryDist(detail.getIndustryDist());
                fundMapper.updateById(fund);
                refreshed++;

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("重仓股补偿失败: fund={}", fundCode, e);
            }
        }
        log.info("重仓股补偿完成, 刷新 {} 只基金", refreshed);
    }

    /**
     * 每交易日19:30同步净值数据
     */
    @Scheduled(cron = "0 30 19 * * MON-FRI")
    public void syncDailyNav() {
        if (!tradingCalendarService.isTradingDay(LocalDate.now())) return;

        log.info("开始同步每日净值数据...");
        List<Fund> funds = fundMapper.selectList(null);
        int count = 0;

        for (Fund fund : funds) {
            try {
                String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
                List<Map<String, Object>> navList = eastMoneyDataSource.getNavHistory(
                        fund.getCode(), today, today);

                for (Map<String, Object> navData : navList) {
                    saveNav(fund.getCode(), navData);
                    count++;
                }
            } catch (Exception e) {
                log.warn("同步净值失败: fund={}", fund.getCode(), e);
            }
        }
        log.info("净值同步完成, 更新 {} 条记录", count);
    }

    /**
     * 每周一20:00同步用户关注基金的重仓股和行业分布数据
     * 基金季报更新后重仓股会变化，需要定期刷新
     */
    @Scheduled(cron = "0 0 20 * * MON")
    public void syncHoldings() {
        log.info("开始同步基金重仓股数据...");

        // 收集用户持仓和自选中的所有基金代码
        Set<String> fundCodes = new HashSet<>();

        try {
            List<Position> positions = positionMapper.selectList(null);
            fundCodes.addAll(positions.stream()
                    .map(Position::getFundCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            log.warn("查询持仓基金列表失败", e);
        }

        try {
            List<Watchlist> watchlists = watchlistMapper.selectList(null);
            fundCodes.addAll(watchlists.stream()
                    .map(Watchlist::getFundCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            log.warn("查询自选基金列表失败", e);
        }

        if (fundCodes.isEmpty()) {
            log.info("无需同步重仓股, 无用户关注的基金");
            return;
        }

        log.info("需要同步重仓股的基金数量: {}", fundCodes.size());
        int successCount = 0;

        for (String fundCode : fundCodes) {
            try {
                FundDetailDTO detail = eastMoneyDataSource.getFundDetail(fundCode);
                if (detail == null) {
                    log.warn("获取基金详情失败, 跳过: {}", fundCode);
                    continue;
                }

                Fund fund = fundMapper.selectById(fundCode);
                if (fund == null) {
                    fund = new Fund();
                    fund.setCode(fundCode);
                    fund.setName(detail.getName());
                    fund.setType(detail.getType());
                    fund.setTopHoldings(detail.getTopHoldings());
                    fund.setIndustryDist(detail.getIndustryDist());
                    if (detail.getHoldingsDate() != null) {
                        fund.setHoldingsDate(LocalDate.parse(detail.getHoldingsDate()));
                    }
                    fundMapper.insert(fund);
                } else {
                    fund.setTopHoldings(detail.getTopHoldings());
                    fund.setIndustryDist(detail.getIndustryDist());
                    if (detail.getHoldingsDate() != null) {
                        fund.setHoldingsDate(LocalDate.parse(detail.getHoldingsDate()));
                    }
                    fundMapper.updateById(fund);
                }
                successCount++;

                // 请求间隔, 避免频率限制
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("重仓股同步被中断");
                break;
            } catch (Exception e) {
                log.warn("同步重仓股失败: fund={}", fundCode, e);
            }
        }

        log.info("重仓股同步完成, 成功 {}/{} 只基金", successCount, fundCodes.size());
    }

    /**
     * 每交易日21:30补充同步净值数据
     * 针对19:30同步时尚未发布净值的基金进行重试
     */
    @Scheduled(cron = "0 30 21 * * MON-FRI")
    public void retrySyncDailyNav() {
        if (!tradingCalendarService.isTradingDay(LocalDate.now())) return;

        log.info("开始补充同步每日净值数据...");
        LocalDate today = LocalDate.now();
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);

        // 收集所有需要关注的基金代码（持仓+自选+已入库）
        Set<String> fundCodes = getTrackedFundCodes();
        List<Fund> allFunds = fundMapper.selectList(null);
        for (Fund f : allFunds) {
            fundCodes.add(f.getCode());
        }

        int count = 0;
        for (String fundCode : fundCodes) {
            try {
                // 跳过已有今日净值的基金
                long exists = fundNavMapper.selectCount(
                        new QueryWrapper<FundNav>()
                                .eq("fund_code", fundCode)
                                .eq("nav_date", today));
                if (exists > 0) continue;

                List<Map<String, Object>> navList = eastMoneyDataSource.getNavHistory(
                        fundCode, todayStr, todayStr);
                for (Map<String, Object> navData : navList) {
                    saveNav(fundCode, navData);
                    count++;
                }

                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("补充同步净值失败: fund={}", fundCode, e);
            }
        }
        log.info("补充净值同步完成, 更新 {} 条记录", count);
    }

    private void saveNav(String fundCode, Map<String, Object> navData) {
        try {
            String navDate = (String) navData.get("navDate");
            if (navDate == null || navDate.isEmpty()) return;

            long exists = fundNavMapper.selectCount(
                    new QueryWrapper<FundNav>()
                            .eq("fund_code", fundCode)
                            .eq("nav_date", navDate));
            if (exists > 0) return;

            FundNav nav = new FundNav();
            nav.setFundCode(fundCode);
            nav.setNavDate(LocalDate.parse(navDate));
            nav.setNav(toBigDecimal(navData.get("nav")));
            nav.setAccNav(toBigDecimal(navData.get("accNav")));
            nav.setDailyReturn(toBigDecimal(navData.get("dailyReturn")));
            fundNavMapper.insert(nav);

            // QDII 基金净值延迟发布(T+1)，需要回填前一天的预测记录
            evaluateQdiiPredictionForNavDate(fundCode, nav.getNavDate(), nav.getNav(), nav.getDailyReturn());
        } catch (Exception e) {
            log.warn("保存净值失败: fund={}", fundCode, e);
        }
    }

    /**
     * 当 QDII 基金净值发布时，回填对应 predict_date 的预测记录
     * QDII 流程：Day N 23:00 快照预测(predict_date=Day N) → Day N+1 22:00 发布净值 → 需要回填 Day N 的预测
     *
     * @param fundCode   基金代码
     * @param navDate    净值日期（实际净值对应的日期）
     * @param actualNav  实际净值
     * @param actualReturn 实际涨跌幅
     */
    private void evaluateQdiiPredictionForNavDate(String fundCode, LocalDate navDate,
                                                   BigDecimal actualNav, BigDecimal actualReturn) {
        try {
            // 查询该基金前一天的未评估预测记录
            // QDII 的 predict_date 是净值日期的前一天（因为预测的是当日海外市场涨跌）
            LocalDate predictDate = navDate.minusDays(1);

            List<EstimatePrediction> pendingPredictions = estimatePredictionMapper.selectList(
                    new QueryWrapper<EstimatePrediction>()
                            .eq("fund_code", fundCode)
                            .eq("predict_date", predictDate)
                            .isNull("actual_return")
            );

            if (pendingPredictions.isEmpty()) {
                return;
            }

            int evaluated = 0;
            for (EstimatePrediction prediction : pendingPredictions) {
                prediction.setActualNav(actualNav);
                prediction.setActualReturn(actualReturn);

                // 计算误差 = 预测涨跌幅 - 实际涨跌幅
                if (prediction.getPredictedReturn() != null && actualReturn != null) {
                    prediction.setReturnError(
                            prediction.getPredictedReturn().subtract(actualReturn)
                                    .setScale(4, RoundingMode.HALF_UP)
                    );
                }

                estimatePredictionMapper.updateById(prediction);
                evaluated++;
                log.debug("QDII 预测评估完成: fund={}, predict_date={}, source={}",
                        fundCode, predictDate, prediction.getSourceKey());
            }

            if (evaluated > 0) {
                log.info("QDII 净值发布后回填预测评估: fund={}, nav_date={}, predict_date={}, 评估 {} 条记录",
                        fundCode, navDate, predictDate, evaluated);
            }
        } catch (Exception e) {
            log.warn("QDII 预测评估回填失败: fund={}, nav_date={}", fundCode, navDate, e);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 快照结果，用于 CLI 重试逻辑
     */
    public static class SnapshotResult {
        private final int saved;
        private final int skipped;
        private final List<String> failedCodes;

        public SnapshotResult(int saved, int skipped, List<String> failedCodes) {
            this.saved = saved;
            this.skipped = skipped;
            this.failedCodes = failedCodes;
        }

        public int getSaved() { return saved; }
        public int getSkipped() { return skipped; }
        public List<String> getFailedCodes() { return failedCodes; }
        public boolean hasFailures() { return !failedCodes.isEmpty(); }
    }

    /**
     * 每交易日14:50快照A股基金估值（收盘前最准确）
     * QDII基金在22:00单独快照，因为海外市场此时未开盘
     */
    @Scheduled(cron = "0 50 14 * * MON-FRI")
    public void snapshotDomesticFundPredictions() {
        if (!tradingCalendarService.isTradingDay(LocalDate.now())) return;
        Set<String> fundCodes = getTrackedFundCodes();
        snapshotEstimates(fundCodes, true);
    }

    /**
     * 每交易日23:00快照QDII基金估值
     * 美股夏令时21:30开盘，冬令时22:30开盘
     * 23:00执行确保夏令时/冬令时都已开盘，估值更准确
     * 港股16:00收盘，此时也已收盘
     */
    @Scheduled(cron = "0 0 23 * * MON-FRI")
    public void snapshotQdiiFundPredictions() {
        if (!tradingCalendarService.isTradingDay(LocalDate.now())) return;
        if (!tradingCalendarService.isUsTradingDay(LocalDate.now())) {
            log.info("今日非美股交易日，跳过QDII快照");
            return;
        }
        Set<String> fundCodes = getTrackedFundCodes();
        snapshotEstimates(fundCodes, false);
    }

    /**
     * 通用估值快照方法（支持指定基金集合，返回失败列表供重试）
     *
     * @param fundCodes    待快照的基金代码集合
     * @param domesticOnly true=仅处理A股基金(跳过QDII), false=仅处理QDII基金(跳过A股)
     * @return 快照结果，包含成功数和失败基金列表
     */
    public SnapshotResult snapshotEstimates(Set<String> fundCodes, boolean domesticOnly) {
        String label = domesticOnly ? "A股" : "QDII";
        log.info("开始快照{}基金估值预测...", label);

        if (fundCodes.isEmpty()) {
            log.info("无需快照预测, 无用户关注的基金");
            return new SnapshotResult(0, 0, Collections.emptyList());
        }

        LocalDate today = LocalDate.now();
        int saved = 0;
        int skipped = 0;
        List<String> failedCodes = new ArrayList<>();

        for (String fundCode : fundCodes) {
            try {
                Fund fund = fundMapper.selectById(fundCode);
                boolean isQdii = fund != null && "QDII".equals(fund.getType());

                // 根据模式过滤
                if (domesticOnly && isQdii) { skipped++; continue; }
                if (!domesticOnly && !isQdii) { skipped++; continue; }

                EstimateSourceDTO estimates = fundDataAggregator.getMultiSourceEstimates(fundCode);
                if (estimates == null || estimates.getSources() == null) {
                    failedCodes.add(fundCode);
                    continue;
                }

                boolean anySaved = false;
                for (EstimateSourceDTO.EstimateItem source : estimates.getSources()) {
                    String key = source.getKey();
                    if (!"eastmoney".equals(key) && !"sina".equals(key)
                            && !"stock".equals(key)) {
                        continue;
                    }
                    if (!source.isAvailable()) continue;

                    long exists = estimatePredictionMapper.selectCount(
                            new QueryWrapper<EstimatePrediction>()
                                    .eq("fund_code", fundCode)
                                    .eq("source_key", key)
                                    .eq("predict_date", today));
                    if (exists > 0) { anySaved = true; continue; }

                    EstimatePrediction prediction = new EstimatePrediction();
                    prediction.setFundCode(fundCode);
                    prediction.setSourceKey(key);
                    prediction.setPredictDate(today);
                    prediction.setPredictedNav(source.getEstimateNav());
                    prediction.setPredictedReturn(source.getEstimateReturn());
                    estimatePredictionMapper.insert(prediction);
                    saved++;
                    anySaved = true;
                }

                // 所有数据源都无可用数据，记入失败列表
                if (!anySaved) {
                    failedCodes.add(fundCode);
                }

                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("快照预测失败: fund={}", fundCode, e);
                failedCodes.add(fundCode);
            }
        }

        log.info("{}基金估值快照完成, 保存 {} 条记录, 跳过 {} 只, 失败 {} 只",
                label, saved, skipped, failedCodes.size());
        return new SnapshotResult(saved, skipped, failedCodes);
    }

    /**
     * 每交易日20:00评估预测准确度（第一批）
     * 主要评估普通A股基金，此时大部分净值已公布
     */
    @Scheduled(cron = "0 0 20 * * MON-FRI")
    public void evaluatePredictionAccuracyBatch1() {
        doEvaluatePredictionAccuracy("20:00批次");
    }

    /**
     * 每交易日21:00评估预测准确度（第二批）
     * 评估港股基金和部分延迟公布的QDII
     */
    @Scheduled(cron = "0 0 21 * * MON-FRI")
    public void evaluatePredictionAccuracyBatch2() {
        doEvaluatePredictionAccuracy("21:00批次");
    }

    /**
     * 每交易日22:00评估预测准确度（第三批）
     * 评估大部分QDII基金（T+1净值通常在21:00-22:00公布）
     */
    @Scheduled(cron = "0 0 22 * * MON-FRI")
    public void evaluatePredictionAccuracyBatch3() {
        doEvaluatePredictionAccuracy("22:00批次");
    }

    /**
     * 执行预测准确度评估
     * @param batchName 批次名称，用于日志
     */
    private void doEvaluatePredictionAccuracy(String batchName) {
        if (!tradingCalendarService.isTradingDay(LocalDate.now())) return;

        log.info("开始评估预测准确度 [{}]...", batchName);
        LocalDate today = LocalDate.now();

        // 查询今日所有未评估的预测记录
        List<EstimatePrediction> pendingPredictions = estimatePredictionMapper.selectList(
                new QueryWrapper<EstimatePrediction>()
                        .eq("predict_date", today)
                        .isNull("actual_return")
        );

        if (pendingPredictions.isEmpty()) {
            log.info("[{}] 无待评估的预测记录", batchName);
            return;
        }

        int evaluated = 0;
        int skipped = 0;
        for (EstimatePrediction prediction : pendingPredictions) {
            try {
                // 查询该基金今日的实际净值
                List<FundNav> navs = fundNavMapper.selectList(
                        new QueryWrapper<FundNav>()
                                .eq("fund_code", prediction.getFundCode())
                                .eq("nav_date", today)
                                .last("LIMIT 1")
                );
                if (navs.isEmpty()) {
                    skipped++;
                    continue;
                }

                FundNav actualNav = navs.get(0);
                prediction.setActualNav(actualNav.getNav());
                prediction.setActualReturn(actualNav.getDailyReturn());

                // 计算误差 = 预测涨跌幅 - 实际涨跌幅
                if (prediction.getPredictedReturn() != null && actualNav.getDailyReturn() != null) {
                    prediction.setReturnError(
                            prediction.getPredictedReturn().subtract(actualNav.getDailyReturn())
                                    .setScale(4, RoundingMode.HALF_UP)
                    );
                }

                estimatePredictionMapper.updateById(prediction);
                evaluated++;
            } catch (Exception e) {
                log.warn("评估预测准确度失败: id={}, fund={}", prediction.getId(), prediction.getFundCode(), e);
            }
        }
        log.info("[{}] 预测准确度评估完成, 评估 {} 条记录, 跳过 {} 条(净值未公布)",
                batchName, evaluated, skipped);
    }

    /**
     * 获取用户持仓和自选中的所有基金代码
     */
    public Set<String> getTrackedFundCodes() {
        Set<String> fundCodes = new HashSet<>();
        try {
            List<Position> positions = positionMapper.selectList(null);
            fundCodes.addAll(positions.stream()
                    .map(Position::getFundCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            log.warn("查询持仓基金列表失败", e);
        }
        try {
            List<Watchlist> watchlists = watchlistMapper.selectList(null);
            fundCodes.addAll(watchlists.stream()
                    .map(Watchlist::getFundCode)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet()));
        } catch (Exception e) {
            log.warn("查询自选基金列表失败", e);
        }
        return fundCodes;
    }
}
