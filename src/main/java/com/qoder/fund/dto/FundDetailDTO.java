package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class FundDetailDTO {
    private String code;
    private String name;
    private String type;
    private String company;
    private String manager;
    private String establishDate;
    private BigDecimal scale;
    private Integer riskLevel;
    private Map<String, Object> feeRate;
    private List<Map<String, Object>> topHoldings;
    private List<Map<String, Object>> industryDist;
    private BigDecimal latestNav;
    private String latestNavDate;
    private BigDecimal estimateNav;
    private BigDecimal estimateReturn;
    private String estimateSource;
    private PerformanceDTO performance;
    private List<Map<String, Object>> sectorChanges;

    @Data
    public static class PerformanceDTO {
        private BigDecimal week1;
        private BigDecimal month1;
        private BigDecimal month3;
        private BigDecimal month6;
        private BigDecimal year1;
        private BigDecimal year3;
        private BigDecimal sinceEstablish;
    }
}
