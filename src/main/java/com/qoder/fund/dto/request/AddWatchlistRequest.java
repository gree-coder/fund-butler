package com.qoder.fund.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AddWatchlistRequest {

    @NotBlank(message = "基金代码不能为空")
    private String fundCode;

    private String groupName = "默认";
}
