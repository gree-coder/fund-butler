package com.qoder.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@TableName(value = "fund", autoResultMap = true)
public class Fund {

    @TableId(type = IdType.INPUT)
    private String code;

    private String name;
    private String type;
    private String company;
    private String manager;
    private LocalDate establishDate;
    private BigDecimal scale;
    private Integer riskLevel;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> feeRate;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> topHoldings;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> allHoldings;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> industryDist;

    private LocalDate holdingsDate;

    private LocalDateTime updatedAt;
}
