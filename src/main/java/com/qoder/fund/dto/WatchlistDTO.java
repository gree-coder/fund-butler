package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class WatchlistDTO {
    private Long id;
    private String fundCode;
    private String fundName;
    private String groupName;
    private BigDecimal latestNav;
    private BigDecimal estimateReturn;
    private BigDecimal actualNav;
    private BigDecimal actualReturn;
    private Map<String, BigDecimal> performance;
}
