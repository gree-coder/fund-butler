package com.qoder.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qoder.fund.datasource.FundDataAggregator;
import com.qoder.fund.dto.EstimateSourceDTO;
import com.qoder.fund.dto.WatchlistDTO;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.entity.Watchlist;
import com.qoder.fund.mapper.FundMapper;
import com.qoder.fund.mapper.WatchlistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistService {

    private final WatchlistMapper watchlistMapper;
    private final FundMapper fundMapper;
    private final FundDataAggregator dataAggregator;

    public Map<String, Object> list(String group) {
        QueryWrapper<Watchlist> query = new QueryWrapper<>();
        if (group != null && !group.isEmpty()) {
            query.eq("group_name", group);
        }
        query.orderByDesc("created_at");
        List<Watchlist> watchlists = watchlistMapper.selectList(query);

        List<WatchlistDTO> dtoList = new ArrayList<>();
        for (Watchlist w : watchlists) {
            WatchlistDTO dto = new WatchlistDTO();
            dto.setId(w.getId());
            dto.setFundCode(w.getFundCode());
            dto.setGroupName(w.getGroupName());

            Fund fund = fundMapper.selectById(w.getFundCode());
            if (fund != null) {
                dto.setFundName(fund.getName());
            } else {
                // 尝试从外部获取
                var detail = dataAggregator.getFundDetail(w.getFundCode());
                if (detail != null) {
                    dto.setFundName(detail.getName());
                }
            }

            // 多源估值（含智能预估）
            EstimateSourceDTO estimates = dataAggregator.getMultiSourceEstimates(w.getFundCode());
            if (estimates != null && estimates.getSources() != null) {
                for (EstimateSourceDTO.EstimateItem source : estimates.getSources()) {
                    if ("eastmoney".equals(source.getKey()) && source.isAvailable()) {
                        dto.setEstimateReturn(source.getEstimateReturn());
                    }
                    if ("actual".equals(source.getKey()) && source.isAvailable()) {
                        dto.setActualNav(source.getEstimateNav());
                        dto.setActualReturn(source.getEstimateReturn());
                        if (source.isDelayed()) {
                            dto.setActualReturnDelayed(true);
                        }
                    }
                    if ("smart".equals(source.getKey()) && source.isAvailable()) {
                        dto.setSmartEstimateReturn(source.getEstimateReturn());
                        dto.setSmartEstimateNav(source.getEstimateNav());
                        dto.setSmartStrategyType(source.getStrategyType());
                        dto.setSmartDescription(source.getDescription());
                        dto.setSmartScenario(source.getScenario());
                        dto.setSmartWeights(source.getWeights());
                        dto.setSmartAccuracyEnhanced(source.isAccuracyEnhanced());
                    }
                }
            }

            BigDecimal latestNav = dataAggregator.getLatestNav(w.getFundCode());
            dto.setLatestNav(latestNav);

            dtoList.add(dto);
        }

        // 获取所有分组
        List<Watchlist> allWatchlists = watchlistMapper.selectList(null);
        List<String> groups = allWatchlists.stream()
                .map(Watchlist::getGroupName)
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("list", dtoList);
        result.put("groups", groups);
        return result;
    }

    public void add(String fundCode, String groupName) {
        if (groupName == null || groupName.isEmpty()) {
            groupName = "默认";
        }

        // 检查是否已存在
        long exists = watchlistMapper.selectCount(
                new QueryWrapper<Watchlist>()
                        .eq("fund_code", fundCode)
                        .eq("group_name", groupName));
        if (exists > 0) {
            throw new IllegalArgumentException("该基金已在自选列表中");
        }

        // 确保基金信息存在
        dataAggregator.getFundDetail(fundCode);

        Watchlist watchlist = new Watchlist();
        watchlist.setFundCode(fundCode);
        watchlist.setGroupName(groupName);
        watchlistMapper.insert(watchlist);
    }

    public void remove(Long id) {
        watchlistMapper.deleteById(id);
    }

    public Set<String> checkExists(List<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) {
            return Set.of();
        }
        List<Watchlist> list = watchlistMapper.selectList(
                new QueryWrapper<Watchlist>().in("fund_code", fundCodes));
        return list.stream()
                .map(Watchlist::getFundCode)
                .collect(Collectors.toSet());
    }
}
