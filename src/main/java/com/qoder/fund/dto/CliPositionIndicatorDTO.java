package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CLI 持仓客观指标 DTO
 * 仅包含事实性数据，不包含任何主观建议
 * 供外部 Agent 自行分析决策
 */
@Data
public class CliPositionIndicatorDTO {

    /**
     * 分析时间
     */
    private String analysisTime;

    /**
     * 持仓总数
     */
    private Integer totalPositions;

    /**
     * 总资产
     */
    private BigDecimal totalAsset;

    /**
     * 总成本
     */
    private BigDecimal totalCost;

    /**
     * 总收益
     */
    private BigDecimal totalProfit;

    /**
     * 总收益率(%)
     */
    private BigDecimal totalProfitRate;

    /**
     * 今日预估收益
     */
    private BigDecimal todayEstimateProfit;

    /**
     * 今日预估收益率(%)
     */
    private BigDecimal todayEstimateReturn;

    /**
     * 各持仓指标列表
     */
    private List<FundIndicator> funds;

    /**
     * 单只基金客观指标
     */
    @Data
    public static class FundIndicator {
        /**
         * 基金代码
         */
        private String fundCode;

        /**
         * 基金名称
         */
        private String fundName;

        /**
         * 基金类型: STOCK/MIXED/BOND/MONEY/QDII/INDEX
         */
        private String fundType;

        /**
         * 持仓比例(%)
         */
        private BigDecimal positionRatio;

        /**
         * 持仓份额
         */
        private BigDecimal shares;

        /**
         * 成本金额
         */
        private BigDecimal costAmount;

        /**
         * 当前市值
         */
        private BigDecimal marketValue;

        /**
         * 持有收益
         */
        private BigDecimal profit;

        /**
         * 持有收益率(%)
         */
        private BigDecimal profitRate;

        /**
         * 今日预估涨幅(%)
         */
        private BigDecimal todayEstimateReturn;

        /**
         * 今日预估收益金额
         */
        private BigDecimal todayEstimateProfit;

        /**
         * 最新净值
         */
        private BigDecimal latestNav;

        /**
         * 历史业绩(%) - 来自天天基金API
         * 键: 1month, 3month, 6month, 1year, 3year
         */
        private Map<String, BigDecimal> performance;

        /**
         * 行业分布 [{industry, ratio}]
         */
        private List<Map<String, Object>> industryDist;
    }
}
