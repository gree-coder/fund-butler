package com.qoder.fund.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qoder.fund.dto.*;
import com.qoder.fund.datasource.FundDataAggregator;
import com.qoder.fund.datasource.StockEstimateDataSource;
import com.qoder.fund.datasource.TiantianFundDataSource;
import com.qoder.fund.mapper.EstimatePredictionMapper;
import com.qoder.fund.mapper.FundMapper;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.service.DashboardService;
import com.qoder.fund.service.FundDiagnosisService;
import com.qoder.fund.service.MarketOverviewService;
import com.qoder.fund.service.PositionRiskWarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 数据分析报告 CLI 命令
 * 面向外部 Agent 提供客观数据供给，仅输出事实性指标，不输出主观建议
 *
 * 使用方式:
 *   fund-cli report market              获取市场概览（大盘指数 + 板块涨跌）
 *   fund-cli report diagnose <code>     获取单只基金诊断数据
 *   fund-cli report risk                获取持仓风险分析报告
 *   fund-cli report positions           获取持仓客观指标数据
 */
@Component
@CommandLine.Command(
        name = "report",
        aliases = {"rpt"},
        description = "数据分析报告（面向外部 Agent 的数据供给接口）",
        subcommands = {
                ReportCommand.MarketCommand.class,
                ReportCommand.DiagnoseCommand.class,
                ReportCommand.RiskCommand.class,
                ReportCommand.PositionsCommand.class
        }
)
@RequiredArgsConstructor
public class ReportCommand implements Callable<Integer> {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        System.out.println();
        System.out.println("数据分析报告 - 面向外部 Agent 的数据供给接口");
        System.out.println("==================================================");
        System.out.println();
        System.out.println("可用子命令:");
        System.out.println("  market              获取市场概览（大盘指数 + 板块涨跌）");
        System.out.println("  diagnose <code>     获取单只基金诊断数据");
        System.out.println("  risk                获取持仓风险分析报告");
        System.out.println("  positions           获取持仓客观指标数据");
        System.out.println();
        System.out.println("所有子命令默认输出 JSON 格式，方便外部 Agent 解析。");
        System.out.println("数据仅包含客观事实性指标，不包含主观投资建议。");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  fund-cli report market");
        System.out.println("  fund-cli report diagnose 161725");
        System.out.println("  fund-cli report risk");
        System.out.println("  fund-cli report positions");
        System.out.println();
        return 0;
    }

    /**
     * 市场概览命令
     * 输出大盘指数 + 板块涨跌数据
     */
    @Component
    @CommandLine.Command(
            name = "market",
            aliases = {"mkt"},
            description = "获取市场概览（大盘指数 + 板块涨跌）"
    )
    @RequiredArgsConstructor
    public static class MarketCommand implements Callable<Integer> {

        private final MarketOverviewService marketOverviewService;

        @Override
        public Integer call() {
            try {
                MarketOverviewDTO overview = marketOverviewService.getMarketOverview();

                // 构建纯数据输出（剥离主观建议字段）
                Map<String, Object> output = new HashMap<>();
                output.put("updateTime", overview.getUpdateTime());
                output.put("marketSentiment", overview.getMarketSentiment());

                // 大盘指数（客观数据）
                if (overview.getIndices() != null) {
                    List<Map<String, Object>> indices = new ArrayList<>();
                    for (MarketOverviewDTO.IndexData index : overview.getIndices()) {
                        Map<String, Object> indexMap = new HashMap<>();
                        indexMap.put("code", index.getCode());
                        indexMap.put("name", index.getName());
                        indexMap.put("currentPoint", index.getCurrentPoint());
                        indexMap.put("changePoint", index.getChangePoint());
                        indexMap.put("changePercent", index.getChangePercent());
                        indexMap.put("volume", index.getVolume());
                        indexMap.put("turnover", index.getTurnover());
                        indexMap.put("trend", index.getTrend());
                        indices.add(indexMap);
                    }
                    output.put("indices", indices);
                }

                // 板块数据（客观数据）
                if (overview.getLeadingSectors() != null) {
                    output.put("leadingSectors", formatSectors(overview.getLeadingSectors()));
                }
                if (overview.getDecliningSectors() != null) {
                    output.put("decliningSectors", formatSectors(overview.getDecliningSectors()));
                }

                // 大盘指数今日涨跌汇总统计（基于实时指数数据）
                if (overview.getIndices() != null && !overview.getIndices().isEmpty()) {
                    int upCount = 0;
                    int downCount = 0;
                    int unchangedCount = 0;
                    String leaderName = null;
                    BigDecimal leaderPct = null;
                    String laggardName = null;
                    BigDecimal laggardPct = null;
                    for (MarketOverviewDTO.IndexData idx : overview.getIndices()) {
                        BigDecimal pct = idx.getChangePercent();
                        if (pct == null) continue;
                        int cmp = pct.compareTo(BigDecimal.ZERO);
                        if (cmp > 0) upCount++;
                        else if (cmp < 0) downCount++;
                        else unchangedCount++;
                        if (leaderPct == null || pct.compareTo(leaderPct) > 0) {
                            leaderPct = pct;
                            leaderName = idx.getName();
                        }
                        if (laggardPct == null || pct.compareTo(laggardPct) < 0) {
                            laggardPct = pct;
                            laggardName = idx.getName();
                        }
                    }
                    Map<String, Object> indexSummary = new HashMap<>();
                    indexSummary.put("upCount", upCount);
                    indexSummary.put("downCount", downCount);
                    indexSummary.put("unchangedCount", unchangedCount);
                    Map<String, Object> leaderObj = new HashMap<>();
                    leaderObj.put("name", leaderName);
                    leaderObj.put("changePercent", leaderPct);
                    indexSummary.put("leader", leaderObj);
                    Map<String, Object> laggardObj = new HashMap<>();
                    laggardObj.put("name", laggardName);
                    laggardObj.put("changePercent", laggardPct);
                    indexSummary.put("laggard", laggardObj);
                    output.put("indexSummary", indexSummary);
                }

                // 大盘指数近期K线走势（客观数据）
                if (overview.getIndexTrends() != null && !overview.getIndexTrends().isEmpty()) {
                    List<Map<String, Object>> trends = new ArrayList<>();
                    for (MarketOverviewDTO.IndexTrend trend : overview.getIndexTrends()) {
                        Map<String, Object> trendMap = new HashMap<>();
                        trendMap.put("code", trend.getCode());
                        trendMap.put("name", trend.getName());
                        trendMap.put("periodChangePercent", trend.getPeriodChangePercent());
                        trendMap.put("change5d", trend.getChange5d());
                        trendMap.put("change10d", trend.getChange10d());
                        if (trend.getDailyData() != null) {
                            List<Map<String, Object>> dailyList = new ArrayList<>();
                            for (MarketOverviewDTO.DailyKLine kline : trend.getDailyData()) {
                                Map<String, Object> kMap = new HashMap<>();
                                kMap.put("date", kline.getDate());
                                kMap.put("open", kline.getOpen());
                                kMap.put("close", kline.getClose());
                                kMap.put("high", kline.getHigh());
                                kMap.put("low", kline.getLow());
                                kMap.put("volume", kline.getVolume());
                                kMap.put("changePercent", kline.getChangePercent());
                                dailyList.add(kMap);
                            }
                            trendMap.put("dailyData", dailyList);
                        }
                        trends.add(trendMap);
                    }
                    output.put("indexTrends", trends);
                }

                // 不包含 portfolioImpact（含主观建议）
                // 不包含 sentimentDescription（含主观描述）

                // 一级行业板块聚合（客观数据）
                if (overview.getSectorCategories() != null) {
                    output.put("sectorCategories", overview.getSectorCategories());
                }

                System.out.println(toJson(output));
                return 0;

            } catch (Exception e) {
                System.err.println("获取市场概览失败: " + e.getMessage());
                return 1;
            }
        }

        private List<Map<String, Object>> formatSectors(List<MarketOverviewDTO.SectorData> sectors) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (MarketOverviewDTO.SectorData sector : sectors) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", sector.getName());
                map.put("changePercent", sector.getChangePercent());
                map.put("leadingStock", sector.getLeadingStock());
                map.put("trend", sector.getTrend());
                result.add(map);
            }
            return result;
        }
    }

    /**
     * 基金诊断命令
     * 输出单只基金的客观分析数据
     */
    @Component
    @CommandLine.Command(
            name = "diagnose",
            aliases = {"diag", "dx"},
            description = "获取单只基金诊断数据"
    )
    @RequiredArgsConstructor
    public static class DiagnoseCommand implements Callable<Integer> {

        @CommandLine.Parameters(index = "0", description = "基金代码")
        private String fundCode;

        private final FundDiagnosisService fundDiagnosisService;

        @Override
        public Integer call() {
            try {
                FundDiagnosisDTO diagnosis = fundDiagnosisService.getFundDiagnosis(fundCode);

                if (diagnosis == null) {
                    System.err.println("无法获取基金诊断数据: " + fundCode);
                    return 1;
                }

                // 构建纯数据输出（保留评分和分析，剥离主观建议）
                Map<String, Object> output = new HashMap<>();
                output.put("fundCode", diagnosis.getFundCode());
                output.put("fundName", diagnosis.getFundName());
                output.put("diagnosisTime", diagnosis.getDiagnosisTime());
                output.put("overallScore", diagnosis.getOverallScore());

                // 维度评分（客观打分）
                if (diagnosis.getDimensionScores() != null) {
                    Map<String, Object> scores = new HashMap<>();
                    scores.put("performance", diagnosis.getDimensionScores().getPerformance());
                    scores.put("risk", diagnosis.getDimensionScores().getRisk());
                    scores.put("valuation", diagnosis.getDimensionScores().getValuation());
                    scores.put("stability", diagnosis.getDimensionScores().getStability());
                    scores.put("cost", diagnosis.getDimensionScores().getCost());
                    output.put("dimensionScores", scores);
                }

                // 估值分析（客观数据）
                if (diagnosis.getValuation() != null) {
                    Map<String, Object> val = new HashMap<>();
                    val.put("status", diagnosis.getValuation().getStatus());
                    val.put("pePercentile", diagnosis.getValuation().getPePercentile());
                    val.put("pbPercentile", diagnosis.getValuation().getPbPercentile());
                    output.put("valuation", val);
                }

                // 业绩分析（客观评级）
                if (diagnosis.getPerformance() != null) {
                    Map<String, Object> perf = new HashMap<>();
                    perf.put("shortTerm", diagnosis.getPerformance().getShortTerm());
                    perf.put("midTerm", diagnosis.getPerformance().getMidTerm());
                    perf.put("longTerm", diagnosis.getPerformance().getLongTerm());
                    perf.put("vsBenchmark", diagnosis.getPerformance().getVsBenchmark());
                    output.put("performance", perf);
                }

                // 风险分析（客观指标）
                if (diagnosis.getRisk() != null) {
                    Map<String, Object> risk = new HashMap<>();
                    risk.put("riskLevel", diagnosis.getRisk().getRiskLevel());
                    risk.put("volatility", diagnosis.getRisk().getVolatility());
                    risk.put("maxDrawdown", diagnosis.getRisk().getMaxDrawdown());
                    output.put("risk", risk);
                }

                // 风险提示（客观警示）
                output.put("riskWarnings", diagnosis.getRiskWarnings());

                // 不包含 recommendation（主观建议）
                // 不包含 positionAdvice（主观建议）
                // 不包含 suitableFor/notSuitableFor（主观建议）

                System.out.println(toJson(output));
                return 0;

            } catch (Exception e) {
                System.err.println("获取基金诊断失败: " + e.getMessage());
                return 1;
            }
        }
    }

    /**
     * 持仓风险分析命令
     * 输出组合级风险评估数据
     */
    @Component
    @CommandLine.Command(
            name = "risk",
            aliases = {"rw", "warning"},
            description = "获取持仓风险分析报告"
    )
    @RequiredArgsConstructor
    public static class RiskCommand implements Callable<Integer> {

        private final PositionRiskWarningService riskWarningService;

        @Override
        public Integer call() {
            try {
                PositionRiskWarningDTO warning = riskWarningService.getRiskWarning();

                if (warning == null) {
                    System.err.println("无法获取持仓风险数据");
                    return 1;
                }

                // 构建纯数据输出
                Map<String, Object> output = new HashMap<>();
                output.put("warningTime", warning.getWarningTime());
                output.put("overallRiskLevel", warning.getOverallRiskLevel());
                output.put("riskScore", warning.getRiskScore());

                // 健康指标（客观评分）
                if (warning.getHealthMetrics() != null) {
                    Map<String, Object> metrics = new HashMap<>();
                    metrics.put("totalPositions", warning.getHealthMetrics().getTotalPositions());
                    metrics.put("industryDiversification", warning.getHealthMetrics().getIndustryDiversification());
                    metrics.put("concentrationScore", warning.getHealthMetrics().getConcentrationScore());
                    metrics.put("riskBalanceScore", warning.getHealthMetrics().getRiskBalanceScore());
                    metrics.put("valuationHealthScore", warning.getHealthMetrics().getValuationHealthScore());
                    metrics.put("overallHealth", warning.getHealthMetrics().getOverallHealth());
                    output.put("healthMetrics", metrics);
                }

                // 风险项列表（客观检测结果）
                if (warning.getRisks() != null) {
                    List<Map<String, Object>> risks = new ArrayList<>();
                    for (PositionRiskWarningDTO.RiskItem risk : warning.getRisks()) {
                        Map<String, Object> riskMap = new HashMap<>();
                        riskMap.put("type", risk.getType());
                        riskMap.put("level", risk.getLevel());
                        riskMap.put("title", risk.getTitle());
                        riskMap.put("description", risk.getDescription());
                        riskMap.put("currentValue", risk.getCurrentValue());
                        riskMap.put("threshold", risk.getThreshold());
                        riskMap.put("relatedItems", risk.getRelatedItems());
                        // 不包含 suggestion（主观建议）
                        risks.add(riskMap);
                    }
                    output.put("risks", risks);
                }

                // 不包含 suggestions（主观优化建议）
                // 不包含 summary（主观摘要文本）

                // 基金类型分布（客观数据）
                if (warning.getCategoryDistribution() != null) {
                    output.put("categoryDistribution", warning.getCategoryDistribution());
                }

                // 板块(行业)分布（客观数据）
                if (warning.getSectorDistribution() != null) {
                    output.put("sectorDistribution", warning.getSectorDistribution());
                }

                System.out.println(toJson(output));
                return 0;

            } catch (Exception e) {
                System.err.println("获取持仓风险分析失败: " + e.getMessage());
                return 1;
            }
        }
    }

    /**
     * 持仓指标命令
     * 输出每只持仓基金的客观指标数据
     */
    @Component
    @CommandLine.Command(
            name = "positions",
            aliases = {"pos", "indicators"},
            description = "获取持仓客观指标数据"
    )
    @RequiredArgsConstructor
    public static class PositionsCommand implements Callable<Integer> {

        private final DashboardService dashboardService;
        private final TiantianFundDataSource tiantianFundDataSource;
        private final FundDataAggregator fundDataAggregator;
        private final FundMapper fundMapper;
        private final StockEstimateDataSource stockEstimateDataSource;
        private final EstimatePredictionMapper estimatePredictionMapper;

        @Override
        public Integer call() {
            try {
                DashboardDTO dashboard = dashboardService.getDashboard();
                List<PositionDTO> positions = dashboard.getPositions();

                if (positions == null || positions.isEmpty()) {
                    System.err.println("暂无持仓数据");
                    return 1;
                }

                CliPositionIndicatorDTO result = new CliPositionIndicatorDTO();
                result.setAnalysisTime(LocalDateTime.now().format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                result.setTotalPositions(positions.size());
                result.setTotalAsset(dashboard.getTotalAsset());
                result.setTotalProfit(dashboard.getTotalProfit());
                result.setTotalProfitRate(dashboard.getTotalProfitRate());
                result.setTodayEstimateProfit(dashboard.getTodayEstimateProfit());
                result.setTodayEstimateReturn(dashboard.getTodayEstimateReturn());

                // 计算总成本
                BigDecimal totalCost = positions.stream()
                        .map(PositionDTO::getCostAmount)
                        .filter(c -> c != null)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                result.setTotalCost(totalCost);

                // 构建各基金指标
                List<CliPositionIndicatorDTO.FundIndicator> funds = new ArrayList<>();
                for (PositionDTO position : positions) {
                    CliPositionIndicatorDTO.FundIndicator indicator = new CliPositionIndicatorDTO.FundIndicator();
                    indicator.setFundCode(position.getFundCode());
                    indicator.setFundName(position.getFundName());
                    indicator.setFundType(position.getFundType());
                    indicator.setShares(position.getShares());
                    indicator.setCostAmount(position.getCostAmount());
                    indicator.setMarketValue(position.getMarketValue());
                    indicator.setProfit(position.getProfit());
                    indicator.setProfitRate(position.getProfitRate());
                    indicator.setTodayEstimateReturn(position.getEstimateReturn());
                    indicator.setTodayEstimateProfit(position.getEstimateProfit());
                    indicator.setLatestNav(position.getLatestNav());
                    indicator.setIndustryDist(position.getIndustryDist());

                    // 计算持仓比例
                    if (dashboard.getTotalAsset() != null
                            && dashboard.getTotalAsset().compareTo(BigDecimal.ZERO) > 0
                            && position.getMarketValue() != null) {
                        indicator.setPositionRatio(position.getMarketValue()
                                .multiply(new BigDecimal("100"))
                                .divide(dashboard.getTotalAsset(), 2, RoundingMode.HALF_UP));
                    }

                    // 获取历史业绩（来自天天基金API）
                    try {
                        Map<String, Object> fundDetail = tiantianFundDataSource.getFundDetail(position.getFundCode());
                        if (fundDetail != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, BigDecimal> performance =
                                    (Map<String, BigDecimal>) fundDetail.get("performance");
                            indicator.setPerformance(performance);
                        }
                    } catch (Exception e) {
                        // 获取业绩失败不影响整体输出
                    }

                    // 获取近期走势（近1个月净值历史）
                    try {
                        indicator.setRecentTrend(
                                calcRecentTrend(position.getFundCode()));
                    } catch (Exception e) {
                        // 走势计算失败不影响整体输出
                    }

                    // 获取重仓股今日表现
                    try {
                        calcTopHoldingsPerformance(position.getFundCode(), indicator);
                    } catch (Exception e) {
                        // 重仓股数据获取失败不影响整体输出
                    }

                    // 获取预估可靠性
                    try {
                        indicator.setEstimateReliability(
                                calcEstimateReliability(position.getFundCode()));
                    } catch (Exception e) {
                        // 可靠性计算失败不影响整体输出
                    }

                    funds.add(indicator);
                }

                result.setFunds(funds);

                System.out.println(toJson(result));
                return 0;

            } catch (Exception e) {
                System.err.println("获取持仓指标失败: " + e.getMessage());
                return 1;
            }
        }

        /**
         * 计算单只基金近期走势指标
         */
        private CliPositionIndicatorDTO.RecentTrend calcRecentTrend(String fundCode) {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(30); // 取30天确保覚盖10个交易日
            DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
            List<Map<String, Object>> navList = fundDataAggregator.getNavHistory(
                    fundCode, start.format(fmt), end.format(fmt));
            if (navList == null || navList.size() < 2) return null;

            // navList 按时间正序（旧→新）
            int size = navList.size();
            BigDecimal latestNav = (BigDecimal) navList.get(size - 1).get("nav");

            CliPositionIndicatorDTO.RecentTrend trend = new CliPositionIndicatorDTO.RecentTrend();
            trend.setChange3d(calcPeriodChange(navList, 3, latestNav));
            trend.setChange5d(calcPeriodChange(navList, 5, latestNav));
            trend.setChange10d(calcPeriodChange(navList, 10, latestNav));

            // 计算连续涨跌天数
            int consecutive = 0;
            for (int i = size - 1; i >= 1; i--) {
                BigDecimal cur = (BigDecimal) navList.get(i).get("nav");
                BigDecimal prev = (BigDecimal) navList.get(i - 1).get("nav");
                int cmp = cur.compareTo(prev);
                if (i == size - 1) {
                    consecutive = (cmp > 0) ? 1 : (cmp < 0) ? -1 : 0;
                } else {
                    if (consecutive > 0 && cmp > 0) consecutive++;
                    else if (consecutive < 0 && cmp < 0) consecutive--;
                    else break;
                }
            }
            trend.setConsecutiveDays(consecutive);

            // 走势方向基于3日涨幅判断
            BigDecimal c3 = trend.getChange3d();
            if (c3 != null) {
                if (c3.compareTo(new BigDecimal("0.5")) > 0) {
                    trend.setTrendDirection("上涨");
                } else if (c3.compareTo(new BigDecimal("-0.5")) < 0) {
                    trend.setTrendDirection("下跌");
                } else {
                    trend.setTrendDirection("震荡");
                }
            }

            return trend;
        }

        private BigDecimal calcPeriodChange(List<Map<String, Object>> navList, int days, BigDecimal latestNav) {
            int size = navList.size();
            if (size <= days) return null;
            BigDecimal baseNav = (BigDecimal) navList.get(size - 1 - days).get("nav");
            if (baseNav == null || baseNav.compareTo(BigDecimal.ZERO) == 0) return null;
            return latestNav.subtract(baseNav)
                    .multiply(new BigDecimal("100"))
                    .divide(baseNav, 2, RoundingMode.HALF_UP);
        }

        /**
         * 获取重仓股今日表现并计算贡献度
         */
        private void calcTopHoldingsPerformance(String fundCode,
                                                 CliPositionIndicatorDTO.FundIndicator indicator) {
            Fund fund = fundMapper.selectById(fundCode);
            if (fund == null) return;
            List<Map<String, Object>> holdings = fund.getTopHoldings();
            if (holdings == null || holdings.isEmpty()) return;

            // 提取股票代码和权重
            List<String> stockCodes = new ArrayList<>();
            Map<String, BigDecimal> ratioMap = new HashMap<>();
            Map<String, String> nameMap = new HashMap<>();
            for (Map<String, Object> h : holdings) {
                String stockCode = String.valueOf(h.get("stockCode"));
                String stockName = String.valueOf(h.getOrDefault("stockName", ""));
                BigDecimal ratio = toBigDecimal(h.get("ratio"));
                if (stockCode.isEmpty() || ratio.compareTo(BigDecimal.ZERO) <= 0) continue;
                String formatted = stockEstimateDataSource.formatStockCodePublic(stockCode);
                if (formatted.isEmpty()) continue;
                stockCodes.add(formatted);
                ratioMap.put(formatted, ratio);
                nameMap.put(formatted, stockName);
            }
            if (stockCodes.isEmpty()) return;

            // 获取股票实时涨跌幅
            Map<String, BigDecimal> stockReturns =
                    stockEstimateDataSource.fetchStockReturnsBatchedPublic(stockCodes);

            List<CliPositionIndicatorDTO.HoldingPerformance> perfList = new ArrayList<>();
            BigDecimal contribution = BigDecimal.ZERO;
            for (String code : stockCodes) {
                BigDecimal change = stockReturns.getOrDefault(code, null);
                BigDecimal weight = ratioMap.get(code);
                CliPositionIndicatorDTO.HoldingPerformance hp = new CliPositionIndicatorDTO.HoldingPerformance();
                hp.setName(nameMap.get(code));
                hp.setWeight(weight);
                hp.setTodayChange(change);
                perfList.add(hp);
                if (change != null) {
                    contribution = contribution.add(weight.multiply(change)
                            .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP));
                }
            }
            indicator.setTopHoldingsPerformance(perfList);
            indicator.setTopHoldingsContribution(
                    contribution.setScale(2, RoundingMode.HALF_UP));
        }

        /**
         * 计算预估可靠性
         */
        private CliPositionIndicatorDTO.EstimateReliability calcEstimateReliability(String fundCode) {
            LocalDate now = LocalDate.now();
            // 查询最近30天的准确度统计
            List<Map<String, Object>> stats = estimatePredictionMapper.getAccuracyStats(
                    fundCode, now.minusDays(30));
            if (stats == null || stats.isEmpty()) return null;

            // 找到MAE最小的数据源作为主数据源
            String primarySource = null;
            BigDecimal bestMae = null;
            for (Map<String, Object> stat : stats) {
                Object maeObj = stat.get("mae");
                BigDecimal mae = maeObj != null ? new BigDecimal(maeObj.toString()) : null;
                if (mae != null && (bestMae == null || mae.compareTo(bestMae) < 0)) {
                    bestMae = mae;
                    primarySource = (String) stat.get("sourceKey");
                }
            }
            if (primarySource == null) return null;

            // 获取主数据源7天/30天MAE
            BigDecimal mae7d = estimatePredictionMapper.getMaeInPeriod(
                    fundCode, primarySource, now.minusDays(7), now);
            BigDecimal mae30d = bestMae;

            CliPositionIndicatorDTO.EstimateReliability reliability =
                    new CliPositionIndicatorDTO.EstimateReliability();
            reliability.setPrimarySource(primarySource);
            reliability.setMae7d(mae7d != null ? mae7d.setScale(2, RoundingMode.HALF_UP) : null);
            reliability.setMae30d(mae30d.setScale(2, RoundingMode.HALF_UP));

            // 可靠性等级
            BigDecimal refMae = mae7d != null ? mae7d : mae30d;
            if (refMae.compareTo(new BigDecimal("0.2")) < 0) {
                reliability.setReliabilityLevel("高");
            } else if (refMae.compareTo(new BigDecimal("0.4")) < 0) {
                reliability.setReliabilityLevel("中");
            } else {
                reliability.setReliabilityLevel("低");
            }
            return reliability;
        }

        private BigDecimal toBigDecimal(Object value) {
            if (value == null) return BigDecimal.ZERO;
            try {
                return new BigDecimal(value.toString().replace("%", "").trim());
            } catch (Exception e) {
                return BigDecimal.ZERO;
            }
        }
    }

    /**
     * 统一 JSON 序列化
     */
    private static String toJson(Object obj) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.INDENT_OUTPUT);
            return mapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
