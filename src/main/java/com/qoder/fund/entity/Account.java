package com.qoder.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("account")
public class Account {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String platform;
    private String icon;
    private LocalDateTime createdAt;
}
