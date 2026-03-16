package com.qoder.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("fund_transaction")
public class FundTransaction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long positionId;
    private String fundCode;
    private String type;
    private BigDecimal amount;
    private BigDecimal shares;
    private BigDecimal price;
    private BigDecimal fee;
    private LocalDate tradeDate;
    private LocalDateTime createdAt;
}
