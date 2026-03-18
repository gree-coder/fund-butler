package com.qoder.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qoder.fund.datasource.FundDataAggregator;
import com.qoder.fund.dto.EstimateSourceDTO;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.dto.request.AddPositionRequest;
import com.qoder.fund.dto.request.AddTransactionRequest;
import com.qoder.fund.entity.*;
import com.qoder.fund.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionMapper positionMapper;
    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;
    private final FundMapper fundMapper;
    private final FundDataAggregator dataAggregator;

    public List<PositionDTO> list(Long accountId) {
        QueryWrapper<Position> query = new QueryWrapper<>();
        if (accountId != null) {
            query.eq("account_id", accountId);
        }
        List<Position> positions = positionMapper.selectList(query);
        List<PositionDTO> result = new ArrayList<>();

        for (Position p : positions) {
            result.add(buildPositionDTO(p));
        }
        return result;
    }

    @Transactional
    public void add(AddPositionRequest req) {
        // 确保基金信息存在
        dataAggregator.getFundDetail(req.getFundCode());

        BigDecimal amount = req.getAmount();
        BigDecimal shares = req.getShares();
        BigDecimal price = req.getPrice();

        // 自动计算 shares 和 price（如果未提供）
        if (shares == null && price == null) {
            // 两者都未提供，使用最新净值计算
            BigDecimal latestNav = dataAggregator.getLatestNav(req.getFundCode());
            if (latestNav == null || latestNav.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("无法获取最新净值，请手动填写份额或成本净值");
            }
            price = latestNav;
            shares = amount.divide(latestNav, 4, RoundingMode.HALF_UP);
        } else if (shares == null) {
            // 有 price 无 shares
            if (price.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("成交净值不能为零");
            }
            shares = amount.divide(price, 4, RoundingMode.HALF_UP);
        } else if (price == null) {
            // 有 shares 无 price
            if (shares.compareTo(BigDecimal.ZERO) == 0) {
                throw new IllegalArgumentException("持有份额不能为零");
            }
            price = amount.divide(shares, 4, RoundingMode.HALF_UP);
        }

        Position position = new Position();
        position.setFundCode(req.getFundCode());
        position.setAccountId(req.getAccountId());
        position.setShares(shares);
        position.setCostAmount(amount);
        positionMapper.insert(position);

        // 创建买入交易记录
        FundTransaction txn = new FundTransaction();
        txn.setPositionId(position.getId());
        txn.setFundCode(req.getFundCode());
        txn.setType("BUY");
        txn.setAmount(amount);
        txn.setShares(shares);
        txn.setPrice(price);
        txn.setFee(BigDecimal.ZERO);
        txn.setTradeDate(req.getTradeDate());
        transactionMapper.insert(txn);
    }

    @Transactional
    public void addTransaction(Long positionId, AddTransactionRequest req) {
        Position position = positionMapper.selectById(positionId);
        if (position == null) {
            throw new IllegalArgumentException("持仓不存在");
        }

        FundTransaction txn = new FundTransaction();
        txn.setPositionId(positionId);
        txn.setFundCode(position.getFundCode());
        txn.setType(req.getType());
        txn.setAmount(req.getAmount());
        txn.setShares(req.getShares());
        txn.setPrice(req.getPrice());
        txn.setFee(req.getFee() != null ? req.getFee() : BigDecimal.ZERO);
        txn.setTradeDate(req.getTradeDate());
        transactionMapper.insert(txn);

        // 更新持仓
        switch (req.getType()) {
            case "BUY" -> {
                position.setShares(position.getShares().add(req.getShares()));
                position.setCostAmount(position.getCostAmount().add(req.getAmount()));
            }
            case "SELL" -> {
                position.setShares(position.getShares().subtract(req.getShares()));
                BigDecimal sellCost = req.getShares().multiply(
                        position.getCostAmount().divide(position.getShares().add(req.getShares()), 4, RoundingMode.HALF_UP));
                position.setCostAmount(position.getCostAmount().subtract(sellCost));
            }
        }
        positionMapper.updateById(position);
    }

    @Transactional
    public void delete(Long id) {
        transactionMapper.delete(new QueryWrapper<FundTransaction>().eq("position_id", id));
        positionMapper.deleteById(id);
    }

    public List<FundTransaction> getTransactions(Long positionId) {
        return transactionMapper.selectList(
                new QueryWrapper<FundTransaction>()
                        .eq("position_id", positionId)
                        .orderByDesc("trade_date"));
    }

    public PositionDTO buildPositionDTO(Position p) {
        PositionDTO dto = new PositionDTO();
        dto.setId(p.getId());
        dto.setFundCode(p.getFundCode());
        dto.setShares(p.getShares());
        dto.setCostAmount(p.getCostAmount());
        dto.setAccountId(p.getAccountId());

        // 账户名称
        if (p.getAccountId() != null) {
            Account account = accountMapper.selectById(p.getAccountId());
            dto.setAccountName(account != null ? account.getName() : "");
        }

        // 基金信息
        Fund fund = fundMapper.selectById(p.getFundCode());
        if (fund != null) {
            dto.setFundName(fund.getName());
            dto.setFundType(fund.getType());
        }

        // 最新净值和估值
        BigDecimal latestNav = dataAggregator.getLatestNav(p.getFundCode());
        Map<String, Object> estimate = dataAggregator.getEstimateNav(p.getFundCode());

        if (latestNav != null) {
            dto.setLatestNav(latestNav);
            BigDecimal marketValue = p.getShares().multiply(latestNav).setScale(2, RoundingMode.HALF_UP);
            dto.setMarketValue(marketValue);
            dto.setProfit(marketValue.subtract(p.getCostAmount()).setScale(2, RoundingMode.HALF_UP));
            if (p.getCostAmount().compareTo(BigDecimal.ZERO) > 0) {
                dto.setProfitRate(dto.getProfit().divide(p.getCostAmount(), 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
            }
        }

        if (estimate != null && !estimate.isEmpty()) {
            dto.setEstimateNav((BigDecimal) estimate.get("estimateNav"));
            dto.setEstimateReturn((BigDecimal) estimate.get("estimateReturn"));
        }

        // 今日实际净值（如果已发布）
        EstimateSourceDTO.EstimateItem actualSource = dataAggregator.getActualSource(p.getFundCode());
        if (actualSource != null && actualSource.isAvailable()) {
            dto.setActualNav(actualSource.getEstimateNav());
            dto.setActualReturn(actualSource.getEstimateReturn());
        }

        return dto;
    }
}
