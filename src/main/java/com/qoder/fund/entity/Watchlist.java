package com.qoder.fund.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("watchlist")
public class Watchlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String fundCode;
    private String groupName;
    private LocalDateTime createdAt;
}
