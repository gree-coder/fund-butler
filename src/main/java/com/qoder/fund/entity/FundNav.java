package com.qoder.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("fund_nav")
public class FundNav {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fundCode;
    private LocalDate navDate;
    private BigDecimal nav;
    private BigDecimal accNav;
    private BigDecimal dailyReturn;
}
