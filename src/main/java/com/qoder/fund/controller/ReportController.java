package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.FundDiagnosisDTO;
import com.qoder.fund.service.FundDiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 数据分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController {

    private final FundDiagnosisService fundDiagnosisService;

    /**
     * 获取基金诊断报告
     *
     * @param fundCode 基金代码
     * @return 诊断报告
     */
    @GetMapping("/fund/{fundCode}/diagnosis")
    public Result<FundDiagnosisDTO> getFundDiagnosis(@PathVariable String fundCode) {
        log.info("获取基金诊断报告: {}", fundCode);

        FundDiagnosisDTO diagnosis = fundDiagnosisService.getFundDiagnosis(fundCode);

        if (diagnosis == null) {
            return Result.error("无法生成诊断报告，请检查基金代码是否正确");
        }

        return Result.success(diagnosis);
    }
}
