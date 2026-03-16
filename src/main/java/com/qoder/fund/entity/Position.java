package com.qoder.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("position")
public class Position {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;
    private String fundCode;
    private BigDecimal shares;
    private BigDecimal costAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
