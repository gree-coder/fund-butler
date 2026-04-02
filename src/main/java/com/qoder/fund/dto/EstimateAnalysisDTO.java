package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 数据源准确度分析 DTO
 */
@Data
public class EstimateAnalysisDTO {

    private String fundCode;
    private String fundName;

    /** 实时估值数据 */
    private CurrentEstimate currentEstimates;

    /** 准确度统计 */
    private AccuracyStats accuracyStats;

    /** 数据补偿记录 */
    private List<CompensationLog> compensationLogs;

    /**
     * 实时估值数据
     */
    @Data
    public static class CurrentEstimate {
        /** 今日实际净值(如有) */
        private BigDecimal actualNav;
        /** 今日实际涨幅 */
        private BigDecimal actualReturn;
        /** 实际净值日期 */
        private LocalDate actualNavDate;
        /** 是否为延迟数据 */
        private Boolean actualReturnDelayed;

        /** 各数据源估值 */
        private List<SourceEstimate> sources;

        /** 智能综合预估 */
        private SmartEstimate smartEstimate;
    }

    /**
     * 单个数据源估值
     */
    @Data
    public static class SourceEstimate {
        private String key;
        private String label;
        private BigDecimal estimateNav;
        private BigDecimal estimateReturn;
        private boolean available;
        /** 当前权重 */
        private BigDecimal weight;
        /** 可信度评分 0-1 */
        private BigDecimal confidence;
        /** 数据源说明 */
        private String description;
    }

    /**
     * 智能综合预估
     */
    @Data
    public static class SmartEstimate {
        private BigDecimal nav;
        private BigDecimal returnRate;
        /** 策略类型 */
        private String strategy;
        /** 场景名称 */
        private String scenario;
        /** 是否应用准确度修正 */
        private boolean accuracyEnhanced;
        /** 各数据源归一化权重 */
        private Map<String, BigDecimal> weights;
        /** 预估说明 */
        private String description;
    }

    /**
     * 准确度统计
     */
    @Data
    public static class AccuracyStats {
        /** 统计周期 */
        private String period;
        /** 各数据源统计 */
        private List<SourceAccuracy> sources;
    }

    /**
     * 单个数据源准确度
     */
    @Data
    public static class SourceAccuracy {
        private String key;
        private String label;
        /** 平均绝对误差(%) */
        private BigDecimal mae;
        /** 预测次数 */
        private Integer predictionCount;
        /** 命中率(误差<0.5%的比例) */
        private BigDecimal hitRate;
        /** 趋势: improving/stable/declining */
        private String trend;
        /** 星级评级 1-5 */
        private Integer rating;
    }

    /**
     * 数据补偿记录
     */
    @Data
    public static class CompensationLog {
        private LocalDate date;
        /** 补偿前净值 */
        private BigDecimal beforeNav;
        /** 补偿前涨幅 */
        private BigDecimal beforeReturn;
        /** 补偿后净值 */
        private BigDecimal afterNav;
        /** 补偿后涨幅 */
        private BigDecimal afterReturn;
        /** 数据来源 */
        private String source;
        /** 补偿类型: PREDICT(预测数据)/ACTUAL(实际净值) */
        private CompensationType type;
        /** 补偿说明 */
        private String reason;
    }

    /**
     * 补偿数据类型
     */
    public enum CompensationType {
        /** 预测数据补偿 */
        PREDICT,
        /** 实际净值补偿 */
        ACTUAL
    }
}
