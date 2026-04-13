package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 调仓时机提醒 DTO
 */
@Data
public class RebalanceTimingDTO {

    /**
     * 分析时间
     */
    private String analysisTime;

    /**
     * 整体市场情绪: bullish(积极)/neutral(中性)/bearish(谨慎)
     */
    private String marketSentiment;

    /**
     * 调仓建议摘要
     */
    private String summary;

    /**
     * 可操作提醒列表
     */
    private List<TimingAlert> alerts;

    /**
     * 持仓基金调仓建议
     */
    private List<FundRebalanceAdvice> fundAdvices;

    /**
     * 市场机会
     */
    private List<MarketOpportunity> opportunities;

    /**
     * 风险提示
     */
    private List<String> riskReminders;

    /**
     * 调仓时机提醒项
     */
    @Data
    public static class TimingAlert {
        /**
         * 提醒类型: buy(买入)/sell(卖出)/hold(持有)/watch(观望)
         */
        private String type;

        /**
         * 优先级: high/medium/low
         */
        private String priority;

        /**
         * 基金代码
         */
        private String fundCode;

        /**
         * 基金名称
         */
        private String fundName;

        /**
         * 触发条件
         */
        private String triggerCondition;

        /**
         * 当前估值状态: undervalued(低估)/fair(合理)/overvalued(高估)
         */
        private String valuationStatus;

        /**
         * 建议操作
         */
        private String suggestedAction;

        /**
         * 建议仓位调整
         */
        private String positionAdjustment;

        /**
         * 理由
         */
        private String reason;

        /**
         * 预期收益/风险
         */
        private String expectedOutcome;
    }

    /**
     * 基金调仓建议
     */
    @Data
    public static class FundRebalanceAdvice {
        /**
         * 基金代码
         */
        private String fundCode;

        /**
         * 基金名称
         */
        private String fundName;

        /**
         * 当前持仓比例
         */
        private BigDecimal currentRatio;

        /**
         * 建议持仓比例
         */
        private BigDecimal suggestedRatio;

        /**
         * 调整方向: increase(增持)/decrease(减持)/maintain(维持)
         */
        private String adjustmentDirection;

        /**
         * 调整幅度
         */
        private String adjustmentRange;

        /**
         * 估值分位
         */
        private BigDecimal valuationPercentile;

        /**
         * 近期表现
         */
        private String recentPerformance;

        /**
         * 建议理由
         */
        private String reason;
    }

    /**
     * 市场机会
     */
    @Data
    public static class MarketOpportunity {
        /**
         * 机会类型
         */
        private String type;

        /**
         * 描述
         */
        private String description;

        /**
         * 建议操作
         */
        private String suggestedAction;

        /**
         *  urgency: immediate(立即)/short-term(短期)/long-term(长期)
         */
        private String urgency;
    }
}
