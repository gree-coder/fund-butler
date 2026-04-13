package com.qoder.fund.service;

import com.qoder.fund.dto.DashboardDTO;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.dto.RebalanceTimingDTO;
import com.qoder.fund.datasource.TiantianFundDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调仓时机提醒服务
 * 基于真实基金业绩数据 + 今日预估涨幅提供调仓建议
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RebalanceTimingService {

    private final DashboardService dashboardService;
    private final TiantianFundDataSource tiantianFundDataSource;

    // 估值分位阈值
    private static final BigDecimal UNDERVALUED_THRESHOLD = new BigDecimal("20");  // 低估阈值 <20%
    private static final BigDecimal OVERVALUED_THRESHOLD = new BigDecimal("80");   // 高估阈值 >80%
    private static final BigDecimal FAIR_LOW_THRESHOLD = new BigDecimal("40");     // 合理偏低 <40%
    private static final BigDecimal FAIR_HIGH_THRESHOLD = new BigDecimal("60");    // 合理偏高 >60%

    // 业绩阈值（历史）
    private static final BigDecimal STRONG_PERFORMANCE_THRESHOLD = new BigDecimal("20");   // 强势表现 >20%
    private static final BigDecimal WEAK_PERFORMANCE_THRESHOLD = new BigDecimal("-10");    // 弱势表现 <-10%

    // 今日预估涨幅阈值（实时）
    private static final BigDecimal TODAY_DROP_THRESHOLD = new BigDecimal("-3");           // 今日大跌 <-3%
    private static final BigDecimal TODAY_SURGE_THRESHOLD = new BigDecimal("3");           // 今日大涨 >3%
    private static final BigDecimal TODAY_STRONG_SURGE_THRESHOLD = new BigDecimal("5");    // 今日暴涨 >5%

    // 持仓比例阈值
    private static final BigDecimal HIGH_POSITION_THRESHOLD = new BigDecimal("25");        // 高持仓 >25%
    private static final BigDecimal LOW_POSITION_THRESHOLD = new BigDecimal("5");          // 低持仓 <5%

    /**
     * 获取调仓时机提醒
     * 结果缓存15分钟（调仓建议需要较频繁更新）
     */
    @Cacheable(value = "rebalanceTiming", key = "'current'", unless = "#result == null", cacheManager = "analysisCacheManager")
    public RebalanceTimingDTO getRebalanceTiming() {
        log.info("开始生成调仓时机提醒");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 获取 Dashboard 数据
            DashboardDTO dashboard = dashboardService.getDashboard();
            List<PositionDTO> positions = dashboard.getPositions();

            if (positions == null || positions.isEmpty()) {
                return createEmptyTiming();
            }

            // 2. 初始化结果
            RebalanceTimingDTO timing = new RebalanceTimingDTO();
            timing.setAnalysisTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // 3. 分析每只基金的调仓时机
            List<RebalanceTimingDTO.TimingAlert> alerts = new ArrayList<>();
            List<RebalanceTimingDTO.FundRebalanceAdvice> fundAdvices = new ArrayList<>();

            for (PositionDTO position : positions) {
                // 生成调仓提醒
                RebalanceTimingDTO.TimingAlert alert = analyzeFundTiming(position, dashboard.getTotalAsset());
                if (alert != null) {
                    alerts.add(alert);
                }

                // 生成调仓建议
                RebalanceTimingDTO.FundRebalanceAdvice advice = generateRebalanceAdvice(position, dashboard.getTotalAsset());
                if (advice != null) {
                    fundAdvices.add(advice);
                }
            }

            timing.setAlerts(alerts);
            timing.setFundAdvices(fundAdvices);

            // 4. 分析整体市场情绪
            timing.setMarketSentiment(analyzeMarketSentiment(positions));

            // 5. 生成摘要
            timing.setSummary(generateSummary(timing.getMarketSentiment(), alerts, fundAdvices));

            // 6. 识别市场机会
            timing.setOpportunities(identifyOpportunities(positions, alerts));

            // 7. 风险提示
            timing.setRiskReminders(generateRiskReminders(alerts));

            log.info("调仓时机提醒生成完成，提醒项: {}, 建议: {}, 耗时: {}ms",
                    alerts.size(), fundAdvices.size(), System.currentTimeMillis() - startTime);

            return timing;

        } catch (Exception e) {
            log.error("生成调仓时机提醒失败", e);
            return createEmptyTiming();
        }
    }

    /**
     * 分析单只基金的调仓时机
     * 综合考虑真实基金业绩 + 今日预估涨幅 + 用户持有收益
     */
    private RebalanceTimingDTO.TimingAlert analyzeFundTiming(PositionDTO position, BigDecimal totalAsset) {
        BigDecimal todayReturn = position.getEstimateReturn(); // 今日预估涨幅
        BigDecimal userProfitRate = position.getProfitRate();  // 用户持有收益率

        // 获取基金真实历史业绩
        Map<String, Object> fundDetail = tiantianFundDataSource.getFundDetail(position.getFundCode());
        Map<String, BigDecimal> performance = null;
        if (fundDetail != null) {
            performance = (Map<String, BigDecimal>) fundDetail.get("performance");
        }

        // 使用近一年业绩作为主要参考
        BigDecimal yearReturn = performance != null ? performance.get("1year") : null;
        BigDecimal month6Return = performance != null ? performance.get("6month") : null;

        // 如果没有获取到业绩数据，使用用户持有收益作为降级
        if (yearReturn == null) {
            yearReturn = userProfitRate;
        }

        if (yearReturn == null) {
            return null;
        }

        RebalanceTimingDTO.TimingAlert alert = new RebalanceTimingDTO.TimingAlert();
        alert.setFundCode(position.getFundCode());
        alert.setFundName(position.getFundName());

        // 计算持仓比例
        BigDecimal positionRatio = BigDecimal.ZERO;
        if (totalAsset != null && totalAsset.compareTo(BigDecimal.ZERO) > 0 && position.getMarketValue() != null) {
            positionRatio = position.getMarketValue()
                    .multiply(new BigDecimal("100"))
                    .divide(totalAsset, 2, RoundingMode.HALF_UP);
        }

        // ========== 优先级1: 今日暴涨 + 已有收益 → 立即止盈 ==========
        if (todayReturn != null
                && todayReturn.compareTo(TODAY_STRONG_SURGE_THRESHOLD) >= 0
                && yearReturn.compareTo(BigDecimal.ZERO) > 0) {
            alert.setType("sell");
            alert.setPriority("high");
            alert.setTriggerCondition("今日暴涨且已有收益");
            alert.setValuationStatus("overvalued");
            alert.setSuggestedAction("建议部分止盈");
            alert.setPositionAdjustment("可减仓10%-20%");
            alert.setReason(String.format("%s今日大涨%.2f%%，短期涨幅较大，建议落袋为安",
                    position.getFundName(), todayReturn));
            alert.setExpectedOutcome("锁定今日收益，规避短期回调风险");
            return alert;
        }

        // ========== 优先级2: 今日大跌 → 逢低关注 ==========
        if (todayReturn != null && todayReturn.compareTo(TODAY_DROP_THRESHOLD) <= 0) {
            alert.setType("buy");
            alert.setPriority("medium");
            alert.setTriggerCondition("今日跌幅较大");
            alert.setValuationStatus("undervalued");
            alert.setSuggestedAction("关注买入机会");
            alert.setPositionAdjustment("可考虑小幅加仓");

            // 结合历史业绩给出更精准的建议
            if (yearReturn.compareTo(new BigDecimal("-10")) < 0) {
                alert.setReason(String.format("%s今日下跌%.2f%%，且近期累计下跌%.1f%%，估值处于低位",
                        position.getFundName(), todayReturn.abs(), yearReturn.abs()));
                alert.setExpectedOutcome("逢低布局，摊薄成本");
            } else {
                alert.setReason(String.format("%s今日回调%.2f%%，短期出现买入机会",
                        position.getFundName(), todayReturn.abs()));
                alert.setExpectedOutcome("短期博弈或长期持有均可考虑");
            }
            return alert;
        }

        // ========== 优先级3: 历史跌幅较大 → 定投机会 ==========
        if (yearReturn.compareTo(WEAK_PERFORMANCE_THRESHOLD) <= 0) {
            alert.setType("buy");
            alert.setPriority("medium");
            alert.setTriggerCondition("近期跌幅较大");
            alert.setValuationStatus("undervalued");
            alert.setSuggestedAction("考虑定投或加仓");
            alert.setPositionAdjustment("可适当增加仓位");
            alert.setReason(String.format("%s近期下跌%.1f%%，估值处于相对低位，适合分批建仓",
                    position.getFundName(), yearReturn.abs()));
            alert.setExpectedOutcome("摊薄成本，长期收益潜力较大");
            return alert;
        }

        // ========== 优先级4: 历史涨幅较大 → 止盈提醒 ==========
        if (yearReturn.compareTo(STRONG_PERFORMANCE_THRESHOLD) >= 0) {
            alert.setType("sell");
            alert.setPriority(positionRatio.compareTo(HIGH_POSITION_THRESHOLD) > 0 ? "high" : "medium");
            alert.setTriggerCondition("近期涨幅较大");
            alert.setValuationStatus("overvalued");
            alert.setSuggestedAction("考虑部分止盈");
            alert.setPositionAdjustment("建议适当减仓");
            alert.setReason(String.format("%s近期上涨%.1f%%，估值偏高，建议落袋为安",
                    position.getFundName(), yearReturn));
            alert.setExpectedOutcome("锁定收益，降低回调风险");
            return alert;
        }

        // ========== 优先级5: 持仓过重再平衡 ==========
        if (yearReturn.compareTo(new BigDecimal("5")) >= 0 && positionRatio.compareTo(HIGH_POSITION_THRESHOLD) > 0) {
            alert.setType("sell");
            alert.setPriority("medium");
            alert.setTriggerCondition("持仓集中且已有收益");
            alert.setValuationStatus("fair");
            alert.setSuggestedAction("适当减仓再平衡");
            alert.setPositionAdjustment("建议减仓至20%以内");
            alert.setReason(String.format("%s占比较高(%.1f%%)且已有收益，建议适当减仓分散风险",
                    position.getFundName(), positionRatio));
            alert.setExpectedOutcome("降低集中风险，优化组合结构");
            return alert;
        }

        return null;
    }

    /**
     * 生成基金调仓建议
     * 基于真实基金业绩数据
     */
    private RebalanceTimingDTO.FundRebalanceAdvice generateRebalanceAdvice(
            PositionDTO position, BigDecimal totalAsset) {

        if (totalAsset == null || totalAsset.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // 获取基金真实历史业绩
        Map<String, Object> fundDetail = tiantianFundDataSource.getFundDetail(position.getFundCode());
        Map<String, BigDecimal> performance = null;
        if (fundDetail != null) {
            performance = (Map<String, BigDecimal>) fundDetail.get("performance");
        }

        // 使用近一年业绩
        BigDecimal year1Return = performance != null ? performance.get("1year") : null;
        // 降级：使用用户持有收益
        if (year1Return == null) {
            year1Return = position.getProfitRate();
        }

        if (year1Return == null) {
            return null;
        }

        // 计算当前持仓比例
        BigDecimal currentRatio = position.getMarketValue() != null ?
                position.getMarketValue().multiply(new BigDecimal("100")).divide(totalAsset, 2, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;

        RebalanceTimingDTO.FundRebalanceAdvice advice = new RebalanceTimingDTO.FundRebalanceAdvice();
        advice.setFundCode(position.getFundCode());
        advice.setFundName(position.getFundName());
        advice.setCurrentRatio(currentRatio);

        // 基于业绩和当前持仓生成建议
        if (year1Return.compareTo(new BigDecimal("-15")) <= 0) {
            // 亏损较多
            advice.setAdjustmentDirection("increase");
            advice.setSuggestedRatio(new BigDecimal("15"));
            advice.setAdjustmentRange("+5%至+10%");
            advice.setValuationPercentile(new BigDecimal("25"));
            advice.setRecentPerformance("近期回调较多");
            advice.setReason("估值处于相对低位，适合逢低加仓摊薄成本");
        } else if (year1Return.compareTo(new BigDecimal("30")) >= 0 && currentRatio.compareTo(new BigDecimal("20")) > 0) {
            // 涨幅大且持仓重
            advice.setAdjustmentDirection("decrease");
            advice.setSuggestedRatio(new BigDecimal("15"));
            advice.setAdjustmentRange("-5%至-10%");
            advice.setValuationPercentile(new BigDecimal("85"));
            advice.setRecentPerformance("表现优异");
            advice.setReason("收益可观且占比较高，建议适当止盈");
        } else if (currentRatio.compareTo(new BigDecimal("3")) < 0) {
            // 持仓过少
            advice.setAdjustmentDirection("increase");
            advice.setSuggestedRatio(new BigDecimal("8"));
            advice.setAdjustmentRange("+3%至+5%");
            advice.setValuationPercentile(new BigDecimal("50"));
            advice.setRecentPerformance("持仓比例偏低");
            advice.setReason("当前配置比例过低，建议适当增加");
        } else {
            // 维持现状
            advice.setAdjustmentDirection("maintain");
            advice.setSuggestedRatio(currentRatio);
            advice.setAdjustmentRange("维持当前");
            advice.setValuationPercentile(new BigDecimal("50"));
            advice.setRecentPerformance("表现平稳");
            advice.setReason("当前配置合理，建议继续持有");
        }

        return advice;
    }

    /**
     * 分析整体市场情绪
     * 基于今日预估涨幅 + 历史业绩综合判断
     */
    private String analyzeMarketSentiment(List<PositionDTO> positions) {
        if (positions.isEmpty()) {
            return "neutral";
        }

        // 统计今日涨跌情况
        int todayUpCount = 0;
        int todayDownCount = 0;
        BigDecimal todayTotalReturn = BigDecimal.ZERO;
        int todayValidCount = 0;

        // 统计历史涨跌情况
        int historyUpCount = 0;
        int historyDownCount = 0;

        for (PositionDTO position : positions) {
            // 今日数据
            if (position.getEstimateReturn() != null) {
                if (position.getEstimateReturn().compareTo(BigDecimal.ZERO) > 0) {
                    todayUpCount++;
                } else if (position.getEstimateReturn().compareTo(BigDecimal.ZERO) < 0) {
                    todayDownCount++;
                }
                todayTotalReturn = todayTotalReturn.add(position.getEstimateReturn());
                todayValidCount++;
            }

            // 历史数据
            if (position.getProfitRate() != null) {
                if (position.getProfitRate().compareTo(BigDecimal.ZERO) > 0) {
                    historyUpCount++;
                } else if (position.getProfitRate().compareTo(BigDecimal.ZERO) < 0) {
                    historyDownCount++;
                }
            }
        }

        // 优先基于今日数据判断（更实时）
        if (todayValidCount > 0) {
            BigDecimal todayAvgReturn = todayTotalReturn.divide(
                    new BigDecimal(todayValidCount), 2, RoundingMode.HALF_UP);

            // 今日暴涨情况
            long todayStrongUpCount = positions.stream()
                    .filter(p -> p.getEstimateReturn() != null
                            && p.getEstimateReturn().compareTo(TODAY_STRONG_SURGE_THRESHOLD) >= 0)
                    .count();

            // 今日大跌情况
            long todayStrongDownCount = positions.stream()
                    .filter(p -> p.getEstimateReturn() != null
                            && p.getEstimateReturn().compareTo(TODAY_DROP_THRESHOLD) <= 0)
                    .count();

            // 判断市场情绪（今日优先）
            if (todayAvgReturn.compareTo(new BigDecimal("2")) > 0 || todayStrongUpCount >= 2) {
                return "bullish";  // 今日大涨或多只暴涨
            } else if (todayAvgReturn.compareTo(new BigDecimal("-2")) < 0 || todayStrongDownCount >= 2) {
                return "bearish";  // 今日大跌或多只大跌
            }
        }

        //  fallback 到历史数据
        int totalUp = historyUpCount + todayUpCount;
        int totalDown = historyDownCount + todayDownCount;

        if (totalUp > totalDown * 1.5) {
            return "bullish";
        } else if (totalDown > totalUp) {
            return "bearish";
        }

        return "neutral";
    }

    /**
     * 生成摘要
     */
    private String generateSummary(String marketSentiment,
                                   List<RebalanceTimingDTO.TimingAlert> alerts,
                                   List<RebalanceTimingDTO.FundRebalanceAdvice> advices) {
        StringBuilder summary = new StringBuilder();

        // 市场情绪描述
        switch (marketSentiment) {
            case "bullish":
                summary.append("当前市场情绪积极，多只基金表现良好；");
                break;
            case "bearish":
                summary.append("当前市场情绪谨慎，部分基金出现回调；");
                break;
            default:
                summary.append("当前市场情绪平稳；");
        }

        // 提醒统计（基于今日涨跌的短期机会）
        long buyAlerts = alerts.stream().filter(a -> "buy".equals(a.getType())).count();
        long sellAlerts = alerts.stream().filter(a -> "sell".equals(a.getType())).count();

        if (buyAlerts > 0) {
            summary.append(String.format("发现%d个短期买入机会，", buyAlerts));
        }
        if (sellAlerts > 0) {
            summary.append(String.format("有%d只基金建议适当止盈，", sellAlerts));
        }

        // 建议统计（基于历史业绩的中长期配置建议）
        long increaseAdvices = advices.stream()
                .filter(a -> "increase".equals(a.getAdjustmentDirection())).count();
        long decreaseAdvices = advices.stream()
                .filter(a -> "decrease".equals(a.getAdjustmentDirection())).count();

        // 区分增持原因：业绩下跌 vs 持仓过低
        long increaseByPerformance = advices.stream()
                .filter(a -> "increase".equals(a.getAdjustmentDirection()))
                .filter(a -> a.getReason() != null && a.getReason().contains("估值处于相对低位"))
                .count();
        long increaseByLowPosition = increaseAdvices - increaseByPerformance;

        if (increaseByPerformance > 0) {
            summary.append(String.format("%d只基金近期回调较多，可考虑逢低加仓；", increaseByPerformance));
        }
        if (increaseByLowPosition > 0) {
            summary.append(String.format("%d只基金配置比例偏低，可适当增加；", increaseByLowPosition));
        }
        if (decreaseAdvices > 0) {
            summary.append(String.format("%d只基金建议适当减持，", decreaseAdvices));
        }

        // 综合判断
        if (alerts.isEmpty() && increaseAdvices == 0 && decreaseAdvices == 0) {
            summary.append("当前持仓结构合理，建议继续保持。");
        } else if (increaseAdvices > 0 && buyAlerts == 0) {
            summary.append("中长期配置有优化空间，短期暂无明确机会。");
        } else {
            summary.append("详情请查看下方建议列表。");
        }

        return summary.toString();
    }

    /**
     * 识别市场机会
     * 基于今日预估 + 历史业绩综合识别
     */
    private List<RebalanceTimingDTO.MarketOpportunity> identifyOpportunities(
            List<PositionDTO> positions, List<RebalanceTimingDTO.TimingAlert> alerts) {
        List<RebalanceTimingDTO.MarketOpportunity> opportunities = new ArrayList<>();

        // ========== 今日机会（高优先级）==========

        // 今日大跌买入机会
        long todayDropCount = positions.stream()
                .filter(p -> p.getEstimateReturn() != null
                        && p.getEstimateReturn().compareTo(TODAY_DROP_THRESHOLD) <= 0)
                .count();

        if (todayDropCount >= 2) {
            RebalanceTimingDTO.MarketOpportunity opp = new RebalanceTimingDTO.MarketOpportunity();
            opp.setType("today_dip");
            opp.setDescription(String.format("今日有%d只基金跌幅超过3%%，出现短期买入机会", todayDropCount));
            opp.setSuggestedAction("关注超跌反弹机会，可考虑小幅加仓");
            opp.setUrgency("immediate");
            opportunities.add(opp);
        }

        // 今日暴涨止盈机会
        long todaySurgeCount = positions.stream()
                .filter(p -> p.getEstimateReturn() != null
                        && p.getEstimateReturn().compareTo(TODAY_STRONG_SURGE_THRESHOLD) >= 0)
                .count();

        if (todaySurgeCount >= 2) {
            RebalanceTimingDTO.MarketOpportunity opp = new RebalanceTimingDTO.MarketOpportunity();
            opp.setType("today_surge");
            opp.setDescription(String.format("今日有%d只基金涨幅超过5%%，建议关注止盈机会", todaySurgeCount));
            opp.setSuggestedAction("短期涨幅较大，可考虑部分减仓");
            opp.setUrgency("immediate");
            opportunities.add(opp);
        }

        // ========== 历史机会（中长期）==========

        // 超跌买入机会
        long historyDropCount = positions.stream()
                .filter(p -> p.getProfitRate() != null && p.getProfitRate().compareTo(new BigDecimal("-10")) < 0)
                .count();

        if (historyDropCount >= 2 && todayDropCount < 2) {
            RebalanceTimingDTO.MarketOpportunity opp = new RebalanceTimingDTO.MarketOpportunity();
            opp.setType("dip_buying");
            opp.setDescription(String.format("有%d只基金近期回调超过10%%，适合逢低布局", historyDropCount));
            opp.setSuggestedAction("可考虑分批定投或适当加仓");
            opp.setUrgency("short-term");
            opportunities.add(opp);
        }

        // 止盈机会
        long profitCount = positions.stream()
                .filter(p -> p.getProfitRate() != null && p.getProfitRate().compareTo(new BigDecimal("25")) > 0)
                .count();

        if (profitCount >= 2 && todaySurgeCount < 2) {
            RebalanceTimingDTO.MarketOpportunity opp = new RebalanceTimingDTO.MarketOpportunity();
            opp.setType("profit_taking");
            opp.setDescription(String.format("有%d只基金收益超过25%%，可考虑适当止盈", profitCount));
            opp.setSuggestedAction("建议部分减仓锁定收益");
            opp.setUrgency("short-term");
            opportunities.add(opp);
        }

        // 如果没有明显机会，给出持有建议
        if (opportunities.isEmpty()) {
            RebalanceTimingDTO.MarketOpportunity opp = new RebalanceTimingDTO.MarketOpportunity();
            opp.setType("hold");
            opp.setDescription("当前市场没有明显的调仓信号");
            opp.setSuggestedAction("建议继续持有，耐心等待机会");
            opp.setUrgency("long-term");
            opportunities.add(opp);
        }

        return opportunities;
    }

    /**
     * 生成风险提示
     */
    private List<String> generateRiskReminders(List<RebalanceTimingDTO.TimingAlert> alerts) {
        List<String> reminders = new ArrayList<>();

        // 追涨风险提示
        boolean hasBuyAlert = alerts.stream().anyMatch(a -> "buy".equals(a.getType()));
        if (hasBuyAlert) {
            reminders.add("逢低加仓需分批进行，避免一次性重仓");
        }

        // 止盈风险提示
        boolean hasSellAlert = alerts.stream().anyMatch(a -> "sell".equals(a.getType()));
        if (hasSellAlert) {
            reminders.add("止盈建议分批减仓，避免错失后续上涨");
        }

        // 通用提醒
        reminders.add("调仓建议仅供参考，请结合自身风险承受能力决策");
        reminders.add("市场有风险，投资需谨慎");

        return reminders;
    }

    /**
     * 创建空提醒（无持仓时）
     */
    private RebalanceTimingDTO createEmptyTiming() {
        RebalanceTimingDTO timing = new RebalanceTimingDTO();
        timing.setAnalysisTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        timing.setMarketSentiment("neutral");
        timing.setSummary("暂无持仓数据，请添加持仓后查看调仓建议");
        timing.setAlerts(new ArrayList<>());
        timing.setFundAdvices(new ArrayList<>());
        timing.setOpportunities(new ArrayList<>());
        timing.setRiskReminders(List.of("市场有风险，投资需谨慎"));
        return timing;
    }
}
