package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.AiFundDiagnosisDTO;
import com.qoder.fund.service.FundDiagnosisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * AI 分析控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiAnalysisController {

    private final FundDiagnosisService fundDiagnosisService;

    /**
     * 获取基金 AI 诊断报告
     *
     * @param fundCode 基金代码
     * @return 诊断报告
     */
    @GetMapping("/fund/{fundCode}/diagnosis")
    public Result<AiFundDiagnosisDTO> getFundDiagnosis(@PathVariable String fundCode) {
        log.info("获取基金AI诊断报告: {}", fundCode);

        AiFundDiagnosisDTO diagnosis = fundDiagnosisService.getFundDiagnosis(fundCode);

        if (diagnosis == null) {
            return Result.error("无法生成诊断报告，请检查基金代码是否正确");
        }

        return Result.success(diagnosis);
    }
}
