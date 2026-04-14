package com.qoder.fund.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 持仓风险预警报告 DTO
 */
@Data
public class PositionRiskWarningDTO {

    /**
     * 预警时间
     */
    private String warningTime;

    /**
     * 风险等级: low(低风险)/medium(中风险)/high(高风险)
     */
    private String overallRiskLevel;

    /**
     * 综合风险评分 (0-100，分数越高风险越大)
     */
    private Integer riskScore;

    /**
     * 预警摘要
     */
    private String summary;

    /**
     * 风险项列表
     */
    private List<RiskItem> risks;

    /**
     * 健康指标
     */
    private HealthMetrics healthMetrics;

    /**
     * 优化建议
     */
    private List<OptimizationSuggestion> suggestions;

    /**
     * 基金类型分布 [{category, count, marketValue, ratio}]
     */
    private List<Map<String, Object>> categoryDistribution;

    /**
     * 板块(行业)分布 [{sector, marketValue, ratio}]
     */
    private List<Map<String, Object>> sectorDistribution;

    /**
     * 风险项
     */
    @Data
    public static class RiskItem {
        /**
         * 风险类型
         */
        private String type;

        /**
         * 风险等级: low/medium/high/critical
         */
        private String level;

        /**
         * 风险标题
         */
        private String title;

        /**
         * 风险描述
         */
        private String description;

        /**
         * 涉及基金/行业
         */
        private List<String> relatedItems;

        /**
         * 当前值
         */
        private String currentValue;

        /**
         * 阈值
         */
        private String threshold;

        /**
         * 建议操作
         */
        private String suggestion;
    }

    /**
     * 健康指标
     */
    @Data
    public static class HealthMetrics {
        /**
         * 持仓基金数量
         */
        private Integer totalPositions;

        /**
         * 行业分散度评分 (0-100)
         */
        private Integer industryDiversification;

        /**
         * 单一持仓集中度评分 (0-100)
         */
        private Integer concentrationScore;

        /**
         * 风险等级平衡评分 (0-100)
         */
        private Integer riskBalanceScore;

        /**
         * 估值健康度评分 (0-100)
         */
        private Integer valuationHealthScore;

        /**
         * 整体健康度 (0-100)
         */
        private Integer overallHealth;
    }

    /**
     * 优化建议
     */
    @Data
    public static class OptimizationSuggestion {
        /**
         * 建议类型
         */
        private String type;

        /**
         * 优先级: high/medium/low
         */
        private String priority;

        /**
         * 建议标题
         */
        private String title;

        /**
         * 建议内容
         */
        private String content;

        /**
         * 预期效果
         */
        private String expectedBenefit;
    }
}
