package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.MarketOverviewDTO;
import com.qoder.fund.service.MarketOverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 市场概览控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/market")
@RequiredArgsConstructor
public class MarketOverviewController {

    private final MarketOverviewService marketOverviewService;

    /**
     * 获取市场概览
     *
     * @return 市场概览数据（大盘指数、板块热度、持仓影响）
     */
    @GetMapping("/overview")
    public Result<MarketOverviewDTO> getMarketOverview() {
        log.info("获取市场概览");

        MarketOverviewDTO overview = marketOverviewService.getMarketOverview();

        if (overview == null) {
            return Result.error("无法获取市场概览数据");
        }

        return Result.success(overview);
    }
}
