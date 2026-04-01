package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

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
}
