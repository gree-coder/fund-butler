package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.DashboardDTO;
import com.qoder.fund.dto.ProfitAnalysisDTO;
import com.qoder.fund.dto.ProfitTrendDTO;
import com.qoder.fund.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public Result<DashboardDTO> getDashboard() {
        return Result.success(dashboardService.getDashboard());
    }

    @GetMapping("/profit-trend")
    public Result<ProfitTrendDTO> getProfitTrend(@RequestParam(defaultValue = "7") int days) {
        return Result.success(dashboardService.getProfitTrend(days));
    }

    /**
     * 获取收益分析数据（收益曲线+回撤分析）
     */
    @GetMapping("/profit-analysis")
    public Result<ProfitAnalysisDTO> getProfitAnalysis(@RequestParam(defaultValue = "30") int days) {
        return Result.success(dashboardService.getProfitAnalysis(days));
    }
}
