package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.RebalanceTimingDTO;
import com.qoder.fund.service.RebalanceTimingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 调仓时机提醒控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/rebalance")
@RequiredArgsConstructor
public class RebalanceTimingController {

    private final RebalanceTimingService rebalanceTimingService;

    /**
     * 获取调仓时机提醒
     *
     * @return 调仓时机提醒报告
     */
    @GetMapping("/timing")
    public Result<RebalanceTimingDTO> getRebalanceTiming() {
        log.info("获取调仓时机提醒");

        RebalanceTimingDTO timing = rebalanceTimingService.getRebalanceTiming();

        if (timing == null) {
            return Result.error("无法获取调仓时机提醒");
        }

        return Result.success(timing);
    }
}
