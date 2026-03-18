package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.request.AddWatchlistRequest;
import com.qoder.fund.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String group) {
        return Result.success(watchlistService.list(group));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody AddWatchlistRequest request) {
        watchlistService.add(request.getFundCode(), request.getGroupName());
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        watchlistService.remove(id);
        return Result.success();
    }

    @GetMapping("/check")
    public Result<Set<String>> check(@RequestParam List<String> codes) {
        return Result.success(watchlistService.checkExists(codes));
    }
}
