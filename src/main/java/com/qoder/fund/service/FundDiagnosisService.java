package com.qoder.fund.service;

import com.qoder.fund.dto.AiFundDiagnosisDTO;
import com.qoder.fund.datasource.TiantianFundDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 基金诊断服务（规则引擎版）
 * 基于外部API数据 + 规则引擎提供基金诊断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundDiagnosisService {

    private final TiantianFundDataSource tiantianDataSource;

    // 评分权重
    private static final BigDecimal WEIGHT_PERFORMANCE = new BigDecimal("0.40");
    private static final BigDecimal WEIGHT_RISK = new BigDecimal("0.25");
    private static final BigDecimal WEIGHT_VALUATION = new BigDecimal("0.20");
    private static final BigDecimal WEIGHT_STABILITY = new BigDecimal("0.10");
    private static final BigDecimal WEIGHT_COST = new BigDecimal("0.05");

    // 业绩评分阈值
    private static final BigDecimal PERF_EXCELLENT = new BigDecimal("20");
    private static final BigDecimal PERF_GOOD = new BigDecimal("10");
    private static final BigDecimal PERF_AVERAGE = new BigDecimal("0");
    private static final BigDecimal PERF_POOR = new BigDecimal("-10");

    /**
     * 获取基金诊断报告
     * 结果缓存1小时
     */
    @Cacheable(value = "aiFundDiagnosis", key = "#fundCode", unless = "#result == null", cacheManager = "aiCacheManager")
    public AiFundDiagnosisDTO getFundDiagnosis(String fundCode) {
        log.info("开始生成基金诊断报告: {}", fundCode);
        long startTime = System.currentTimeMillis();

        try {
            // 1. 获取天天基金数据
            Map<String, Object> fundData = tiantianDataSource.getFundDetail(fundCode);
            if (fundData == null || fundData.isEmpty()) {
                log.warn("无法获取基金数据: {}", fundCode);
                return createFallbackDiagnosis(fundCode);
            }

            // 2. 构建诊断报告
            AiFundDiagnosisDTO diagnosis = buildDiagnosis(fundCode, fundData);

            log.info("基金诊断报告生成完成: {}, 耗时: {}ms", fundCode,
                    System.currentTimeMillis() - startTime);

            return diagnosis;

        } catch (Exception e) {
            log.error("生成基金诊断报告失败: {}", fundCode, e);
            return createFallbackDiagnosis(fundCode);
        }
    }

    /**
     * 构建诊断报告
     */
    @SuppressWarnings("unchecked")
    private AiFundDiagnosisDTO buildDiagnosis(String fundCode, Map<String, Object> fundData) {
        AiFundDiagnosisDTO diagnosis = new AiFundDiagnosisDTO();
        diagnosis.setFundCode(fundCode);
        diagnosis.setFundName((String) fundData.getOrDefault("fundName", "未知"));
        diagnosis.setDiagnosisTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 获取业绩数据
        Map<String, BigDecimal> performance = (Map<String, BigDecimal>) fundData.get("performance");
        if (performance == null) {
            performance = new java.util.HashMap<>();
        }

        // 1. 计算各维度评分
        int performanceScore = calculatePerformanceScore(performance);
        int riskScore = calculateRiskScore(fundData);
        int valuationScore = calculateValuationScore(performance);
        int stabilityScore = calculateStabilityScore(fundData);
        int costScore = calculateCostScore(fundData);

        // 2. 计算综合评分
        BigDecimal overallScore = new BigDecimal(performanceScore).multiply(WEIGHT_PERFORMANCE)
                .add(new BigDecimal(riskScore).multiply(WEIGHT_RISK))
                .add(new BigDecimal(valuationScore).multiply(WEIGHT_VALUATION))
                .add(new BigDecimal(stabilityScore).multiply(WEIGHT_STABILITY))
                .add(new BigDecimal(costScore).multiply(WEIGHT_COST));

        diagnosis.setOverallScore(overallScore.intValue());

        // 3. 生成投资建议
        String recommendation = generateRecommendation(overallScore.intValue(), performance);
        diagnosis.setRecommendation(recommendation);

        // 4. 计算信心等级
        int confidenceLevel = calculateConfidenceLevel(overallScore.intValue(), fundData);
        diagnosis.setConfidenceLevel(confidenceLevel);

        // 5. 生成摘要
        diagnosis.setSummary(generateSummary(fundData, performance, overallScore.intValue()));

        // 6. 设置维度评分
        AiFundDiagnosisDTO.DimensionScores dimensionScores = new AiFundDiagnosisDTO.DimensionScores();
        dimensionScores.setPerformance(performanceScore);
        dimensionScores.setRisk(riskScore);
        dimensionScores.setValuation(valuationScore);
        dimensionScores.setStability(stabilityScore);
        dimensionScores.setCost(costScore);
        diagnosis.setDimensionScores(dimensionScores);

        // 7. 生成估值分析
        diagnosis.setValuation(generateValuationAnalysis(performance, valuationScore));

        // 8. 生成业绩分析
        diagnosis.setPerformance(generatePerformanceAnalysis(performance, performanceScore));

        // 9. 生成风险分析
        diagnosis.setRisk(generateRiskAnalysis(fundData, riskScore));

        // 10. 生成持仓建议
        diagnosis.setPositionAdvice(generatePositionAdvice(overallScore.intValue(), performance, fundData));

        // 11. 生成风险提示
        diagnosis.setRiskWarnings(generateRiskWarnings(fundData, performance));

        // 12. 生成适合人群
        diagnosis.setSuitableFor(generateSuitableFor(fundData, riskScore));
        diagnosis.setNotSuitableFor(generateNotSuitableFor(fundData, riskScore));

        return diagnosis;
    }

    /**
     * 计算业绩评分
     */
    private int calculatePerformanceScore(Map<String, BigDecimal> performance) {
        BigDecimal year1 = performance.getOrDefault("1year", BigDecimal.ZERO);
        BigDecimal year3 = performance.getOrDefault("3year", BigDecimal.ZERO);
        BigDecimal month6 = performance.getOrDefault("6month", BigDecimal.ZERO);

        // 加权计算: 1年(50%) + 3年(30%) + 6月(20%)
        BigDecimal weightedPerf = year1.multiply(new BigDecimal("0.5"))
                .add(year3.multiply(new BigDecimal("0.3")))
                .add(month6.multiply(new BigDecimal("0.2")));

        // 映射到0-100分
        if (weightedPerf.compareTo(PERF_EXCELLENT) >= 0) {
            return 90 + weightedPerf.subtract(PERF_EXCELLENT).intValue();
        } else if (weightedPerf.compareTo(PERF_GOOD) >= 0) {
            return 75 + weightedPerf.subtract(PERF_GOOD).multiply(new BigDecimal("1.5")).intValue();
        } else if (weightedPerf.compareTo(PERF_AVERAGE) >= 0) {
            return 60 + weightedPerf.multiply(new BigDecimal("1.5")).intValue();
        } else if (weightedPerf.compareTo(PERF_POOR) >= 0) {
            return 40 + weightedPerf.subtract(PERF_POOR).multiply(new BigDecimal("2")).intValue();
        } else {
            return Math.max(20, 40 + weightedPerf.subtract(PERF_POOR).multiply(new BigDecimal("2")).intValue());
        }
    }

    /**
     * 计算风险评分
     */
    private int calculateRiskScore(Map<String, Object> fundData) {
        int riskLevel = (int) fundData.getOrDefault("riskLevel", 3);
        BigDecimal maxDrawdown = (BigDecimal) fundData.getOrDefault("maxDrawdown", BigDecimal.ZERO);
        BigDecimal sharpeRatio = (BigDecimal) fundData.getOrDefault("sharpeRatio", BigDecimal.ZERO);

        // 基础分
        int baseScore = 70;

        // 风险等级调整 (1-5级，级别越高风险越大)
        baseScore -= (riskLevel - 3) * 10;

        // 最大回撤调整
        if (maxDrawdown.compareTo(new BigDecimal("-30")) < 0) {
            baseScore -= 15;
        } else if (maxDrawdown.compareTo(new BigDecimal("-20")) < 0) {
            baseScore -= 5;
        }

        // 夏普比率调整
        if (sharpeRatio.compareTo(new BigDecimal("1.5")) > 0) {
            baseScore += 10;
        } else if (sharpeRatio.compareTo(new BigDecimal("1")) > 0) {
            baseScore += 5;
        } else if (sharpeRatio.compareTo(new BigDecimal("0")) < 0) {
            baseScore -= 10;
        }

        return Math.max(20, Math.min(95, baseScore));
    }

    /**
     * 计算估值评分
     */
    private int calculateValuationScore(Map<String, BigDecimal> performance) {
        BigDecimal month1 = performance.getOrDefault("1month", BigDecimal.ZERO);
        BigDecimal month3 = performance.getOrDefault("3month", BigDecimal.ZERO);

        // 近期表现反映估值状态
        BigDecimal recentPerf = month1.multiply(new BigDecimal("0.6"))
                .add(month3.multiply(new BigDecimal("0.4")));

        if (recentPerf.compareTo(new BigDecimal("10")) > 0) {
            return 65; // 短期涨幅大，估值偏高
        } else if (recentPerf.compareTo(new BigDecimal("5")) > 0) {
            return 75;
        } else if (recentPerf.compareTo(new BigDecimal("-5")) > 0) {
            return 80; // 估值适中
        } else if (recentPerf.compareTo(new BigDecimal("-15")) > 0) {
            return 85; // 近期回调，估值偏低
        } else {
            return 90; // 回调较大，估值较低
        }
    }

    /**
     * 计算稳定性评分
     */
    private int calculateStabilityScore(Map<String, Object> fundData) {
        BigDecimal fundSize = (BigDecimal) fundData.getOrDefault("fundSize", BigDecimal.ZERO);
        String establishDate = (String) fundData.getOrDefault("establishDate", "");

        int score = 70;

        // 规模稳定性 (2-100亿为佳)
        if (fundSize.compareTo(new BigDecimal("2")) >= 0 && fundSize.compareTo(new BigDecimal("100")) <= 0) {
            score += 10;
        } else if (fundSize.compareTo(new BigDecimal("100")) > 0) {
            score += 5;
        } else if (fundSize.compareTo(new BigDecimal("0.5")) < 0) {
            score -= 10;
        }

        // 成立年限 (3年以上为佳)
        if (!establishDate.isEmpty()) {
            try {
                int year = Integer.parseInt(establishDate.substring(0, 4));
                int currentYear = LocalDateTime.now().getYear();
                int years = currentYear - year;
                if (years >= 5) {
                    score += 10;
                } else if (years >= 3) {
                    score += 5;
                }
            } catch (Exception e) {
                // 忽略解析错误
            }
        }

        return Math.max(50, Math.min(95, score));
    }

    /**
     * 计算费率评分
     */
    private int calculateCostScore(Map<String, Object> fundData) {
        BigDecimal managementFee = (BigDecimal) fundData.getOrDefault("managementFee", new BigDecimal("1.5"));

        // 管理费率越低越好
        if (managementFee.compareTo(new BigDecimal("0.5")) <= 0) {
            return 95;
        } else if (managementFee.compareTo(new BigDecimal("1")) <= 0) {
            return 85;
        } else if (managementFee.compareTo(new BigDecimal("1.5")) <= 0) {
            return 70;
        } else {
            return 55;
        }
    }

    /**
     * 生成投资建议
     */
    private String generateRecommendation(int overallScore, Map<String, BigDecimal> performance) {
        BigDecimal year1 = performance.getOrDefault("1year", BigDecimal.ZERO);

        if (overallScore >= 80 && year1.compareTo(BigDecimal.ZERO) > 0) {
            return "bullish";
        } else if (overallScore >= 60) {
            return "neutral";
        } else {
            return "bearish";
        }
    }

    /**
     * 计算信心等级
     */
    private int calculateConfidenceLevel(int overallScore, Map<String, Object> fundData) {
        int shanghaiRating = (int) fundData.getOrDefault("shanghaiSecRating", 0);

        int level = overallScore / 20;
        if (shanghaiRating >= 4) {
            level += 1;
        }

        return Math.max(1, Math.min(5, level));
    }

    /**
     * 生成摘要
     */
    private String generateSummary(Map<String, Object> fundData, Map<String, BigDecimal> performance, int score) {
        String fundName = (String) fundData.getOrDefault("fundName", "该基金");
        String fundType = (String) fundData.getOrDefault("fundType", "混合型");
        BigDecimal year1 = performance.getOrDefault("1year", BigDecimal.ZERO);

        StringBuilder summary = new StringBuilder();
        summary.append(fundName).append("是一只").append(fundType).append("基金，");

        if (score >= 80) {
            summary.append("整体表现优秀，综合评分").append(score).append("分。");
        } else if (score >= 65) {
            summary.append("整体表现良好，综合评分").append(score).append("分。");
        } else if (score >= 50) {
            summary.append("整体表现一般，综合评分").append(score).append("分。");
        } else {
            summary.append("整体表现偏弱，综合评分").append(score).append("分。");
        }

        if (year1.compareTo(new BigDecimal("15")) > 0) {
            summary.append("近一年业绩表现突出，涨幅达").append(year1).append("%。");
        } else if (year1.compareTo(BigDecimal.ZERO) > 0) {
            summary.append("近一年取得正收益").append(year1).append("%。");
        } else {
            summary.append("近一年收益为负，需关注市场变化。");
        }

        return summary.toString();
    }

    /**
     * 生成估值分析
     */
    private AiFundDiagnosisDTO.ValuationAnalysis generateValuationAnalysis(
            Map<String, BigDecimal> performance, int score) {
        AiFundDiagnosisDTO.ValuationAnalysis valuation = new AiFundDiagnosisDTO.ValuationAnalysis();

        BigDecimal month1 = performance.getOrDefault("1month", BigDecimal.ZERO);

        if (month1.compareTo(new BigDecimal("10")) > 0) {
            valuation.setStatus("偏高");
            valuation.setDescription("近期涨幅较大，估值处于相对高位，建议关注回调风险。");
        } else if (month1.compareTo(new BigDecimal("-10")) < 0) {
            valuation.setStatus("偏低");
            valuation.setDescription("近期回调较多，估值处于相对低位，可关注布局机会。");
        } else {
            valuation.setStatus("适中");
            valuation.setDescription("估值处于合理区间，适合长期持有。");
        }

        valuation.setPePercentile(new BigDecimal(score));
        valuation.setPbPercentile(new BigDecimal(score));

        return valuation;
    }

    /**
     * 生成业绩分析
     */
    private AiFundDiagnosisDTO.PerformanceAnalysis generatePerformanceAnalysis(
            Map<String, BigDecimal> performance, int score) {
        AiFundDiagnosisDTO.PerformanceAnalysis perfAnalysis = new AiFundDiagnosisDTO.PerformanceAnalysis();

        BigDecimal year1 = performance.getOrDefault("1year", BigDecimal.ZERO);
        BigDecimal year3 = performance.getOrDefault("3year", BigDecimal.ZERO);
        BigDecimal month6 = performance.getOrDefault("6month", BigDecimal.ZERO);

        // 短期评价
        if (month6.compareTo(new BigDecimal("10")) > 0) {
            perfAnalysis.setShortTerm("优秀");
        } else if (month6.compareTo(BigDecimal.ZERO) > 0) {
            perfAnalysis.setShortTerm("良好");
        } else {
            perfAnalysis.setShortTerm("一般");
        }

        // 中期评价
        if (year1.compareTo(new BigDecimal("15")) > 0) {
            perfAnalysis.setMidTerm("优秀");
        } else if (year1.compareTo(new BigDecimal("5")) > 0) {
            perfAnalysis.setMidTerm("良好");
        } else if (year1.compareTo(BigDecimal.ZERO) > 0) {
            perfAnalysis.setMidTerm("一般");
        } else {
            perfAnalysis.setMidTerm("偏弱");
        }

        // 长期评价
        if (year3.compareTo(new BigDecimal("30")) > 0) {
            perfAnalysis.setLongTerm("优秀");
        } else if (year3.compareTo(new BigDecimal("15")) > 0) {
            perfAnalysis.setLongTerm("良好");
        } else if (year3.compareTo(BigDecimal.ZERO) > 0) {
            perfAnalysis.setLongTerm("一般");
        } else {
            perfAnalysis.setLongTerm("偏弱");
        }

        perfAnalysis.setVsBenchmark(score >= 70 ? "跑赢基准" : "跑输基准");
        perfAnalysis.setDescription("该基金" + perfAnalysis.getMidTerm() + "，" + perfAnalysis.getLongTerm() + "。");

        return perfAnalysis;
    }

    /**
     * 生成风险分析
     */
    private AiFundDiagnosisDTO.RiskAnalysis generateRiskAnalysis(Map<String, Object> fundData, int score) {
        AiFundDiagnosisDTO.RiskAnalysis risk = new AiFundDiagnosisDTO.RiskAnalysis();

        int riskLevel = (int) fundData.getOrDefault("riskLevel", 3);
        BigDecimal maxDrawdown = (BigDecimal) fundData.getOrDefault("maxDrawdown", BigDecimal.ZERO);

        risk.setRiskLevel(riskLevel);

        if (score >= 75) {
            risk.setVolatility("较低");
            risk.setMaxDrawdown("较小");
            risk.setDescription("风险控制在较好水平，适合稳健型投资者。");
        } else if (score >= 60) {
            risk.setVolatility("中等");
            risk.setMaxDrawdown("中等");
            risk.setDescription("风险水平适中，需关注市场波动。");
        } else {
            risk.setVolatility("较高");
            risk.setMaxDrawdown("较大");
            risk.setDescription("风险水平较高，波动可能较大，需谨慎投资。");
        }

        return risk;
    }

    /**
     * 生成持仓建议
     */
    private AiFundDiagnosisDTO.PositionAdvice generatePositionAdvice(
            int overallScore, Map<String, BigDecimal> performance, Map<String, Object> fundData) {
        AiFundDiagnosisDTO.PositionAdvice advice = new AiFundDiagnosisDTO.PositionAdvice();

        BigDecimal year1 = performance.getOrDefault("1year", BigDecimal.ZERO);

        if (overallScore >= 80 && year1.compareTo(BigDecimal.ZERO) > 0) {
            advice.setSuggestion("增持");
            advice.setReason("该基金综合表现优秀，业绩稳健，建议适当增加配置。");
            advice.setSuggestedRatio(new BigDecimal("15"));
        } else if (overallScore >= 65) {
            advice.setSuggestion("持有");
            advice.setReason("该基金表现良好，建议继续持有，关注市场变化。");
            advice.setSuggestedRatio(new BigDecimal("10"));
        } else if (overallScore >= 50) {
            advice.setSuggestion("观望");
            advice.setReason("该基金表现一般，建议保持观望，等待更好的入场时机。");
            advice.setSuggestedRatio(new BigDecimal("5"));
        } else {
            advice.setSuggestion("减持");
            advice.setReason("该基金表现偏弱，建议适当减仓，控制风险。");
            advice.setSuggestedRatio(new BigDecimal("3"));
        }

        return advice;
    }

    /**
     * 生成风险提示
     */
    private List<String> generateRiskWarnings(Map<String, Object> fundData, Map<String, BigDecimal> performance) {
        List<String> warnings = new ArrayList<>();

        int riskLevel = (int) fundData.getOrDefault("riskLevel", 3);
        BigDecimal year1 = performance.getOrDefault("1year", BigDecimal.ZERO);
        BigDecimal month1 = performance.getOrDefault("1month", BigDecimal.ZERO);

        if (riskLevel >= 4) {
            warnings.add("该基金风险等级较高，波动可能较大");
        }

        if (year1.compareTo(new BigDecimal("-10")) < 0) {
            warnings.add("近一年业绩表现偏弱，需关注基本面变化");
        }

        if (month1.compareTo(new BigDecimal("10")) > 0) {
            warnings.add("近期涨幅较大，注意回调风险");
        }

        if (warnings.isEmpty()) {
            warnings.add("市场有风险，投资需谨慎");
        }

        return warnings;
    }

    /**
     * 生成适合人群
     */
    private List<String> generateSuitableFor(Map<String, Object> fundData, int riskScore) {
        List<String> suitable = new ArrayList<>();
        String fundType = (String) fundData.getOrDefault("fundType", "混合型");

        if (fundType.contains("股票") || fundType.contains("指数")) {
            suitable.add("有一定风险承受能力的投资者");
            suitable.add("追求长期资本增值的投资者");
        } else if (fundType.contains("债券")) {
            suitable.add("风险偏好较低的投资者");
            suitable.add("追求稳定收益的投资者");
        } else {
            suitable.add("风险偏好中等的投资者");
            suitable.add("希望平衡收益与风险的投资者");
        }

        if (riskScore >= 70) {
            suitable.add("注重风险控制的投资者");
        }

        return suitable;
    }

    /**
     * 生成不适合人群
     */
    private List<String> generateNotSuitableFor(Map<String, Object> fundData, int riskScore) {
        List<String> notSuitable = new ArrayList<>();
        int riskLevel = (int) fundData.getOrDefault("riskLevel", 3);

        if (riskLevel >= 4) {
            notSuitable.add("风险厌恶型投资者");
            notSuitable.add("短期资金需求者");
        }

        if (riskScore < 60) {
            notSuitable.add("追求稳定收益的投资者");
        }

        notSuitable.add("无法承受本金损失的投资者");

        return notSuitable;
    }

    /**
     * 创建降级诊断报告
     */
    private AiFundDiagnosisDTO createFallbackDiagnosis(String fundCode) {
        AiFundDiagnosisDTO diagnosis = new AiFundDiagnosisDTO();
        diagnosis.setFundCode(fundCode);
        diagnosis.setFundName("未知");
        diagnosis.setDiagnosisTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        diagnosis.setOverallScore(50);
        diagnosis.setRecommendation("neutral");
        diagnosis.setConfidenceLevel(3);
        diagnosis.setSummary("暂时无法获取基金诊断数据，请稍后重试。");

        AiFundDiagnosisDTO.DimensionScores scores = new AiFundDiagnosisDTO.DimensionScores();
        scores.setValuation(50);
        scores.setPerformance(50);
        scores.setRisk(50);
        scores.setStability(50);
        scores.setCost(50);
        diagnosis.setDimensionScores(scores);

        AiFundDiagnosisDTO.PositionAdvice advice = new AiFundDiagnosisDTO.PositionAdvice();
        advice.setSuggestion("观望");
        advice.setReason("数据暂时不可用，建议等待完整分析后再做决策");
        advice.setSuggestedRatio(new BigDecimal("5"));
        diagnosis.setPositionAdvice(advice);

        diagnosis.setRiskWarnings(List.of("数据获取失败，请稍后重试"));
        diagnosis.setSuitableFor(new ArrayList<>());
        diagnosis.setNotSuitableFor(new ArrayList<>());

        return diagnosis;
    }
}
