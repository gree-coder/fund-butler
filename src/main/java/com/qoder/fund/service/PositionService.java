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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionMapper positionMapper;
    private final TransactionMapper transactionMapper;
    private final AccountMapper accountMapper;
    private final FundMapper fundMapper;
    private final FundDataAggregator dataAggregator;
    private final BatchEstimateService batchEstimateService;

    public List<PositionDTO> list(Long accountId) {
        QueryWrapper<Position> query = new QueryWrapper<>();
        if (accountId != null) {
            query.eq("account_id", accountId);
        }
        List<Position> positions = positionMapper.selectList(query);

        if (positions.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量获取关联数据，避免 N+1 查询
        Set<String> fundCodes = positions.stream()
                .map(Position::getFundCode)
                .collect(Collectors.toSet());
        Set<Long> accountIds = positions.stream()
                .map(Position::getAccountId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());

        // 批量查询基金信息
        Map<String, Fund> fundMap = fundCodes.isEmpty() ? new HashMap<>() :
                fundMapper.selectBatchIds(fundCodes).stream()
                        .collect(Collectors.toMap(Fund::getCode, f -> f));

        // 批量查询账户信息
        Map<Long, Account> accountMap = accountIds.isEmpty() ? new HashMap<>() :
                accountMapper.selectBatchIds(accountIds).stream()
                        .collect(Collectors.toMap(Account::getId, a -> a));

        // 批量预同步今日净值（带延迟，避免LSJZ API限流）
        dataAggregator.ensureTodayNavSynced(fundCodes);

        // 使用批量估值服务优化查询性能
        BatchEstimateService.BatchEstimateResult batchResult = batchEstimateService
                .batchGetPositionEstimates(fundCodes);

        List<PositionDTO> result = new ArrayList<>();
        for (Position p : positions) {
            result.add(buildPositionDTOWithBatchResult(p, fundMap, accountMap, batchResult));
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
                // 检查份额是否足够
                if (position.getShares().compareTo(req.getShares()) < 0) {
                    throw new IllegalArgumentException("卖出份额不能大于持仓份额");
                }
                // 使用加权平均成本计算卖出成本
                BigDecimal avgCost = position.getCostAmount().divide(position.getShares(), 4, RoundingMode.HALF_UP);
                BigDecimal sellCost = req.getShares().multiply(avgCost);
                position.setShares(position.getShares().subtract(req.getShares()));
                position.setCostAmount(position.getCostAmount().subtract(sellCost));
            }
            default -> throw new IllegalArgumentException("Invalid transaction type: " + req.getType());
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

        // 主数据源估值为空时，降级到多源智能估值（解决C类份额等天天基金API不支持的情况）
        if (dto.getEstimateReturn() == null) {
            try {
                EstimateSourceDTO estimates = dataAggregator.getMultiSourceEstimates(p.getFundCode());
                if (estimates != null && estimates.getSources() != null) {
                    for (EstimateSourceDTO.EstimateItem source : estimates.getSources()) {
                        if ("smart".equals(source.getKey()) && source.isAvailable()) {
                            dto.setEstimateReturn(source.getEstimateReturn());
                            dto.setEstimateNav(source.getEstimateNav());
                            break;
                        }
                    }
                    // 顺便从多源结果中提取实际涨幅，避免重复API调用
                    for (EstimateSourceDTO.EstimateItem source : estimates.getSources()) {
                        if ("actual".equals(source.getKey()) && source.isAvailable()) {
                            dto.setActualNav(source.getEstimateNav());
                            dto.setActualReturn(source.getEstimateReturn());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("多源智能估值降级失败: {}", p.getFundCode(), e);
            }
        }

        // 今日实际净值（如果已发布且尚未从多源结果中获取）
        if (dto.getActualReturn() == null) {
            EstimateSourceDTO.EstimateItem actualSource = dataAggregator.getActualSource(p.getFundCode());
            if (actualSource != null && actualSource.isAvailable()) {
                dto.setActualNav(actualSource.getEstimateNav());
                dto.setActualReturn(actualSource.getEstimateReturn());
            }
        }

        // QDII等基金净值延迟发布，降级展示最近一个交易日的实际涨幅
        if (dto.getActualReturn() == null && "QDII".equals(dto.getFundType())) {
            EstimateSourceDTO.EstimateItem latestActual = dataAggregator.getLatestActualSource(p.getFundCode());
            if (latestActual != null && latestActual.isAvailable()) {
                dto.setActualNav(latestActual.getEstimateNav());
                dto.setActualReturn(latestActual.getEstimateReturn());
                dto.setActualReturnDelayed(true);
            }
        }

        return dto;
    }

    /**
     * 优化的 PositionDTO 构建方法，使用批量查询结果避免 N+1
     */
    private PositionDTO buildPositionDTOOptimized(Position p, Map<String, Fund> fundMap,
                                                   Map<Long, Account> accountMap, Map<String, BigDecimal> latestNavMap,
                                                   Map<String, Map<String, Object>> estimateMap) {
        PositionDTO dto = new PositionDTO();
        dto.setId(p.getId());
        dto.setFundCode(p.getFundCode());
        dto.setShares(p.getShares());
        dto.setCostAmount(p.getCostAmount());
        dto.setAccountId(p.getAccountId());

        // 账户名称（从批量查询结果获取）
        if (p.getAccountId() != null) {
            Account account = accountMap.get(p.getAccountId());
            dto.setAccountName(account != null ? account.getName() : "");
        }

        // 基金信息（从批量查询结果获取）
        Fund fund = fundMap.get(p.getFundCode());
        if (fund != null) {
            dto.setFundName(fund.getName());
            dto.setFundType(fund.getType());
        }

        // 最新净值和估值（从批量查询结果获取）
        BigDecimal latestNav = latestNavMap.get(p.getFundCode());
        Map<String, Object> estimate = estimateMap.get(p.getFundCode());

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

        // 主数据源估值为空时，降级到多源智能估值
        if (dto.getEstimateReturn() == null) {
            try {
                EstimateSourceDTO estimates = dataAggregator.getMultiSourceEstimates(p.getFundCode());
                if (estimates != null && estimates.getSources() != null) {
                    for (EstimateSourceDTO.EstimateItem source : estimates.getSources()) {
                        if ("smart".equals(source.getKey()) && source.isAvailable()) {
                            dto.setEstimateReturn(source.getEstimateReturn());
                            dto.setEstimateNav(source.getEstimateNav());
                            break;
                        }
                    }
                    for (EstimateSourceDTO.EstimateItem source : estimates.getSources()) {
                        if ("actual".equals(source.getKey()) && source.isAvailable()) {
                            dto.setActualNav(source.getEstimateNav());
                            dto.setActualReturn(source.getEstimateReturn());
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("多源智能估值降级失败: {}", p.getFundCode(), e);
            }
        }

        // 今日实际净值
        if (dto.getActualReturn() == null) {
            EstimateSourceDTO.EstimateItem actualSource = dataAggregator.getActualSource(p.getFundCode());
            if (actualSource != null && actualSource.isAvailable()) {
                dto.setActualNav(actualSource.getEstimateNav());
                dto.setActualReturn(actualSource.getEstimateReturn());
            }
        }

        // QDII等基金净值延迟发布
        if (dto.getActualReturn() == null && "QDII".equals(dto.getFundType())) {
            EstimateSourceDTO.EstimateItem latestActual = dataAggregator.getLatestActualSource(p.getFundCode());
            if (latestActual != null && latestActual.isAvailable()) {
                dto.setActualNav(latestActual.getEstimateNav());
                dto.setActualReturn(latestActual.getEstimateReturn());
                dto.setActualReturnDelayed(true);
            }
        }

        return dto;
    }

    /**
     * 使用批量估值结果构建 PositionDTO（优化版本）
     */
    private PositionDTO buildPositionDTOWithBatchResult(Position p,
                                                        Map<String, Fund> fundMap,
                                                        Map<Long, Account> accountMap,
                                                        BatchEstimateService.BatchEstimateResult batchResult) {
        PositionDTO dto = new PositionDTO();
        dto.setId(p.getId());
        dto.setFundCode(p.getFundCode());
        dto.setShares(p.getShares());
        dto.setCostAmount(p.getCostAmount());
        dto.setAccountId(p.getAccountId());

        // 账户名称（从批量查询结果获取）
        if (p.getAccountId() != null) {
            Account account = accountMap.get(p.getAccountId());
            dto.setAccountName(account != null ? account.getName() : "");
        }

        // 基金信息（从批量查询结果获取）
        Fund fund = fundMap.get(p.getFundCode());
        if (fund != null) {
            dto.setFundName(fund.getName());
            dto.setFundType(fund.getType());
        }

        // 最新净值（从批量结果获取）
        BigDecimal latestNav = batchResult.latestNavMap().get(p.getFundCode());
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

        // 估值信息（从批量结果获取）
        Map<String, Object> estimate = batchResult.estimateMap().get(p.getFundCode());
        if (estimate != null && !estimate.isEmpty()) {
            dto.setEstimateNav((BigDecimal) estimate.get("estimateNav"));
            dto.setEstimateReturn((BigDecimal) estimate.get("estimateReturn"));
        }

        // 智能估值和实际净值（从多源批量结果获取）
        if (dto.getEstimateReturn() == null) {
            BigDecimal smartReturn = batchResult.getSmartEstimateReturn(p.getFundCode());
            if (smartReturn != null) {
                dto.setEstimateReturn(smartReturn);
                // 估算净值 = 最新净值 * (1 + 涨跌幅/100)
                if (latestNav != null) {
                    BigDecimal estimateNav = latestNav.multiply(
                            BigDecimal.ONE.add(smartReturn.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))
                    ).setScale(4, RoundingMode.HALF_UP);
                    dto.setEstimateNav(estimateNav);
                }
            }
        }

        // 实际净值
        BigDecimal actualReturn = batchResult.getActualReturn(p.getFundCode());
        if (actualReturn != null) {
            dto.setActualReturn(actualReturn);
            if (latestNav != null) {
                BigDecimal actualNav = latestNav.multiply(
                        BigDecimal.ONE.add(actualReturn.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))
                ).setScale(4, RoundingMode.HALF_UP);
                dto.setActualNav(actualNav);
            }
        }

        // QDII等基金净值延迟发布
        if (dto.getActualReturn() == null && "QDII".equals(dto.getFundType())) {
            EstimateSourceDTO dto2 = batchResult.multiSourceEstimateMap().get(p.getFundCode());
            if (dto2 != null) {
                dto2.getSources().stream()
                        .filter(s -> "actual".equals(s.getKey()) && s.isAvailable() && s.isDelayed())
                        .findFirst()
                        .ifPresent(source -> {
                            dto.setActualNav(source.getEstimateNav());
                            dto.setActualReturn(source.getEstimateReturn());
                            dto.setActualReturnDelayed(true);
                        });
            }
        }

        return dto;
    }
}
