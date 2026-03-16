package com.qoder.fund.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class AddTransactionRequest {

    @NotBlank(message = "交易类型不能为空")
    private String type;

    @NotNull(message = "交易金额不能为空")
    private BigDecimal amount;

    @NotNull(message = "交易份额不能为空")
    private BigDecimal shares;

    @NotNull(message = "成交净值不能为空")
    private BigDecimal price;

    private BigDecimal fee;

    @NotNull(message = "交易日期不能为空")
    private LocalDate tradeDate;
}
