package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class EstimateSourceDTO {

    private List<EstimateItem> sources;

    @Data
    public static class EstimateItem {
        private String key;
        private String label;
        private BigDecimal estimateNav;
        private BigDecimal estimateReturn;
        private boolean available;
        private String description;
    }
}
