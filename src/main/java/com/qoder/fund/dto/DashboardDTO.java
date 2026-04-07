package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class DashboardDTO {
    private BigDecimal totalAsset;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private BigDecimal todayProfit;
    /** 今日收益是否为预估值（实际净值未公布时为true） */
    private Boolean todayProfitIsEstimate;
    /** 今日预估总收益金额 */
    private BigDecimal todayEstimateProfit;
    /** 今日预估涨幅 = 今日预估收益 / 总资产 * 100 */
    private BigDecimal todayEstimateReturn;
    private List<PositionDTO> positions;
    /** 聚合行业分布 [{industry, ratio, marketValue}] */
    private List<Map<String, Object>> industryDistribution;
}
