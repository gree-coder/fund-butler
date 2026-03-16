package com.qoder.fund.controller;

import com.qoder.fund.common.Result;
import com.qoder.fund.entity.Account;
import com.qoder.fund.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping
    public Result<List<Account>> list() {
        return Result.success(accountService.list());
    }

    @PostMapping
    public Result<Void> create(@RequestBody Map<String, String> body) {
        accountService.create(body.get("name"), body.get("platform"));
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        accountService.delete(id);
        return Result.success();
    }
}
