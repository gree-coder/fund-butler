package com.qoder.fund.dto;

import lombok.Data;

@Data
public class RefreshResultDTO {
    private FundDetailDTO detail;
    private EstimateSourceDTO estimates;
}
