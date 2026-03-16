package com.qoder.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qoder.fund.entity.Account;
import com.qoder.fund.entity.Position;
import com.qoder.fund.mapper.AccountMapper;
import com.qoder.fund.mapper.PositionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountMapper accountMapper;
    private final PositionMapper positionMapper;

    public List<Account> list() {
        return accountMapper.selectList(null);
    }

    public void create(String name, String platform) {
        Account account = new Account();
        account.setName(name);
        account.setPlatform(platform);
        account.setIcon(platform);
        accountMapper.insert(account);
    }

    public void delete(Long id) {
        long count = positionMapper.selectCount(
                new QueryWrapper<Position>().eq("account_id", id));
        if (count > 0) {
            throw new IllegalArgumentException("该账户下还有持仓，无法删除");
        }
        accountMapper.deleteById(id);
    }
}
