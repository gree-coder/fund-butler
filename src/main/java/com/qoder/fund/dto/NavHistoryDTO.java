package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class NavHistoryDTO {
    private List<String> dates;
    private List<BigDecimal> navs;
}
