package com.qoder.fund.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AddPositionRequest {

    @NotBlank(message = "基金代码不能为空")
    private String fundCode;

    private Long accountId;

    @NotNull(message = "买入金额不能为空")
    private BigDecimal amount;

    private BigDecimal shares;

    private BigDecimal price;

    @NotNull(message = "交易日期不能为空")
    private LocalDate tradeDate;
}
