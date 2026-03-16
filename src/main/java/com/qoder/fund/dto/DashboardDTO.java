package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DashboardDTO {
    private BigDecimal totalAsset;
    private BigDecimal totalProfit;
    private BigDecimal totalProfitRate;
    private BigDecimal todayProfit;
    private List<PositionDTO> positions;
}
