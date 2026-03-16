package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.dto.request.AddPositionRequest;
import com.qoder.fund.dto.request.AddTransactionRequest;
import com.qoder.fund.entity.FundTransaction;
import com.qoder.fund.service.PositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public Result<List<PositionDTO>> list(@RequestParam(required = false) Long accountId) {
        return Result.success(positionService.list(accountId));
    }

    @PostMapping
    public Result<Void> add(@Valid @RequestBody AddPositionRequest request) {
        positionService.add(request);
        return Result.success();
    }

    @PutMapping("/{id}/transaction")
    public Result<Void> addTransaction(
            @PathVariable Long id,
            @Valid @RequestBody AddTransactionRequest request) {
        positionService.addTransaction(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        positionService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}/transactions")
    public Result<List<FundTransaction>> getTransactions(@PathVariable Long id) {
        return Result.success(positionService.getTransactions(id));
    }
}
