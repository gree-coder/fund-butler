package com.qoder.fund.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qoder.fund.dto.*;
import com.qoder.fund.datasource.TiantianFundDataSource;
import com.qoder.fund.service.DashboardService;
import com.qoder.fund.service.FundDiagnosisService;
import com.qoder.fund.service.MarketOverviewService;
import com.qoder.fund.service.PositionRiskWarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

                // 大盘指数近期走势（客观K线数据）
                if (overview.getIndexTrends() != null && !overview.getIndexTrends().isEmpty()) {
                    List<Map<String, Object>> trends = new ArrayList<>();
                    for (MarketOverviewDTO.IndexTrend trend : overview.getIndexTrends()) {
                        Map<String, Object> trendMap = new HashMap<>();
                        trendMap.put("code", trend.getCode());
                        trendMap.put("name", trend.getName());
                        trendMap.put("periodChangePercent", trend.getPeriodChangePercent());
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
