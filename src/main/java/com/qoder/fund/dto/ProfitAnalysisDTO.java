package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收益分析DTO - 包含收益曲线和回撤分析数据
 */
@Data
public class ProfitAnalysisDTO {

    /** 日期列表 */
    private List<String> dates;

    /** 每日收益金额列表 */
    private List<BigDecimal> dailyProfits;

    /** 累计收益金额列表 */
    private List<BigDecimal> cumulativeProfits;

    /** 累计收益率列表 (%) */
    private List<BigDecimal> cumulativeReturns;

    /** 每日市值列表 */
    private List<BigDecimal> marketValues;

    /** 回撤数据 */
    private DrawdownData drawdown;

    /** 统计指标 */
    private PerformanceMetrics metrics;

    @Data
    public static class DrawdownData {
        /** 最大回撤率 (%) */
        private BigDecimal maxDrawdown;
        /** 最大回撤金额 */
        private BigDecimal maxDrawdownAmount;
        /** 最大回撤开始日期 */
        private String startDate;
        /** 最大回撤结束日期 */
        private String endDate;
        /** 回撤持续天数 */
        private Integer duration;
        /** 回撤曲线（每日回撤率） */
        private List<BigDecimal> drawdownCurve;
    }

    @Data
    public static class PerformanceMetrics {
        /** 总收益率 (%) */
        private BigDecimal totalReturn;
        /** 年化收益率 (%) */
        private BigDecimal annualizedReturn;
        /** 夏普比率（简化版，无风险利率按3%计算） */
        private BigDecimal sharpeRatio;
        /** 收益波动率 (%) */
        private BigDecimal volatility;
        /** 盈利天数 */
        private Integer profitDays;
        /** 亏损天数 */
        private Integer lossDays;
        /** 胜率 (%) */
        private BigDecimal winRate;
    }
}
