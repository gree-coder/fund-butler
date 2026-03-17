package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;

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
    private BigDecimal marketValue;
    private BigDecimal profit;
    private BigDecimal profitRate;
    private Long accountId;
    private String accountName;
}
