package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
public class PositionDTO {
    private Long id;
    private String fundCode;
    private String fundName;
    private String fundType;
    private BigDecimal shares;
    private BigDecimal costAmount;
    private BigDecimal latestNav;
    private BigDecimal estimateNav;
    private BigDecimal estimateReturn;
    /** 今日预估收益金额 = 持仓市值 × 今日预估涨幅 / 100 */
    private BigDecimal estimateProfit;
    private BigDecimal actualNav;
    private BigDecimal actualReturn;
    /** 实际涨幅是否为延迟数据(QDII T+1) */
    private Boolean actualReturnDelayed;
    /** 实际涨幅对应的净值日期(QDII延迟时显示) */
    private LocalDate actualNavDate;
    private BigDecimal marketValue;
    private BigDecimal profit;
    private BigDecimal profitRate;
    private Long accountId;
    private String accountName;
    /** 行业分布列表 [{industry, ratio}] */
    private List<Map<String, Object>> industryDist;
}
