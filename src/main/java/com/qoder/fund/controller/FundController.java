package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.EstimateSourceDTO;
import com.qoder.fund.dto.FundDetailDTO;
import com.qoder.fund.dto.FundSearchDTO;
import com.qoder.fund.dto.NavHistoryDTO;
import com.qoder.fund.dto.RefreshResultDTO;
import com.qoder.fund.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fund")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    @GetMapping("/search")
    public Result<Map<String, Object>> search(@RequestParam String keyword) {
        List<FundSearchDTO> list = fundService.search(keyword);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        return Result.success(result);
    }

    @GetMapping("/{code}")
    public Result<FundDetailDTO> getDetail(@PathVariable String code) {
        FundDetailDTO detail = fundService.getDetail(code);
        if (detail == null) {
            return Result.error(404, "基金不存在");
        }
        return Result.success(detail);
    }

    @GetMapping("/{code}/nav-history")
    public Result<NavHistoryDTO> getNavHistory(
            @PathVariable String code,
            @RequestParam(defaultValue = "3m") String period) {
        return Result.success(fundService.getNavHistory(code, period));
    }

    @GetMapping("/{code}/estimates")
    public Result<EstimateSourceDTO> getEstimates(@PathVariable String code) {
        return Result.success(fundService.getMultiSourceEstimates(code));
    }

    @PostMapping("/{code}/refresh")
    public Result<RefreshResultDTO> refresh(@PathVariable String code) {
        RefreshResultDTO result = fundService.refreshFundData(code);
        if (result == null || result.getDetail() == null) {
            return Result.error(404, "基金不存在");
        }
        return Result.success(result);
    }
}
