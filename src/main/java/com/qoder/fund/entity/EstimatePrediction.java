package com.qoder.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("estimate_prediction")
public class EstimatePrediction {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fundCode;
    private String sourceKey;
    private LocalDate predictDate;
    private BigDecimal predictedNav;
    private BigDecimal predictedReturn;
    private BigDecimal actualNav;
    private BigDecimal actualReturn;
    private BigDecimal returnError;
}
