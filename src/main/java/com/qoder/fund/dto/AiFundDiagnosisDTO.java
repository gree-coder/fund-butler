package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 基金诊断报告 DTO
 */
@Data
public class AiFundDiagnosisDTO {

    /**
     * 基金代码
     */
    private String fundCode;

    /**
     * 基金名称
     */
    private String fundName;

    /**
     * 诊断时间
     */
    private String diagnosisTime;

    /**
     * 综合评分 (0-100)
     */
    private Integer overallScore;

    /**
     * 投资建议: bullish(看涨)/neutral(中性)/bearish(看跌)
     */
    private String recommendation;

    /**
     * 投资信心 (1-5星)
     */
    private Integer confidenceLevel;

    /**
     * 诊断摘要
     */
    private String summary;

    /**
     * 多维度评分
     */
    private DimensionScores dimensionScores;

    /**
     * 估值分析
     */
    private ValuationAnalysis valuation;

    /**
     * 业绩分析
     */
    private PerformanceAnalysis performance;

    /**
     * 风险分析
     */
    private RiskAnalysis risk;

    /**
     * 持仓建议
     */
    private PositionAdvice positionAdvice;

    /**
     * 风险提示
     */
    private List<String> riskWarnings;

    /**
     * 适合人群
     */
    private List<String> suitableFor;

    /**
     * 不适合人群
     */
    private List<String> notSuitableFor;

    @Data
    public static class DimensionScores {
        private Integer valuation;      // 估值合理性 (0-100)
        private Integer performance;    // 业绩表现 (0-100)
        private Integer risk;           // 风险控制 (0-100)
        private Integer stability;      // 稳定性 (0-100)
        private Integer cost;           // 费率优势 (0-100)
    }

    @Data
    public static class ValuationAnalysis {
        private String status;          // 低估/适中/高估
        private BigDecimal pePercentile;    // PE 历史分位
        private BigDecimal pbPercentile;    // PB 历史分位
        private String description;     // 估值描述
    }

    @Data
    public static class PerformanceAnalysis {
        private String shortTerm;       // 短期表现: 优秀/良好/一般/较差
        private String midTerm;         // 中期表现
        private String longTerm;        // 长期表现
        private String vsBenchmark;     // 相对基准表现
        private String description;     // 业绩描述
    }

    @Data
    public static class RiskAnalysis {
        private Integer riskLevel;      // 风险等级 1-5
        private String volatility;      // 波动率评价
        private String maxDrawdown;     // 最大回撤评价
        private String description;     // 风险描述
    }

    @Data
    public static class PositionAdvice {
        private String suggestion;      // 建议: 增持/持有/减持/观望
        private String reason;          // 建议理由
        private BigDecimal suggestedRatio;  // 建议仓位比例 (%)
    }
}
