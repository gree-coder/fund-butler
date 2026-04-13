package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.PositionRiskWarningDTO;
import com.qoder.fund.service.PositionRiskWarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 持仓风险预警控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/risk")
@RequiredArgsConstructor
public class PositionRiskController {

    private final PositionRiskWarningService riskWarningService;

    /**
     * 获取持仓风险预警报告
     *
     * @return 风险预警报告
     */
    @GetMapping("/warning")
    public Result<PositionRiskWarningDTO> getRiskWarning() {
        log.info("获取持仓风险预警报告");

        PositionRiskWarningDTO warning = riskWarningService.getRiskWarning();

        if (warning == null) {
            return Result.error("无法获取风险预警报告");
        }

        return Result.success(warning);
    }
}
