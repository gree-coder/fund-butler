package com.qoder.fund.datasource;

import com.qoder.fund.dto.FundDetailDTO;
import com.qoder.fund.dto.FundSearchDTO;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.entity.FundNav;
import com.qoder.fund.mapper.FundMapper;
import com.qoder.fund.mapper.FundNavMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * 多数据源聚合器
 * 主数据源查询 → 失败时降级到备用源 → 估值兜底
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundDataAggregator {

    private final EastMoneyDataSource eastMoneyDataSource;
    private final StockEstimateDataSource stockEstimateDataSource;
    private final FundMapper fundMapper;
    private final FundNavMapper fundNavMapper;

    /**
     * 搜索基金(带缓存)
     */
    @Cacheable(value = "fundSearch", key = "#keyword", unless = "#result == null || #result.isEmpty()")
    public List<FundSearchDTO> searchFund(String keyword) {
        return eastMoneyDataSource.searchFund(keyword);
    }

    /**
     * 获取基金详情(带缓存)
     */
    @Cacheable(value = "fundDetail", key = "#fundCode", unless = "#result == null")
    public FundDetailDTO getFundDetail(String fundCode) {
        FundDetailDTO detail = eastMoneyDataSource.getFundDetail(fundCode);
        if (detail == null) {
            log.warn("主数据源获取详情失败: {}", fundCode);
            return null;
        }

        // 如果估值为空，尝试股票兜底
        if (detail.getEstimateReturn() == null || detail.getEstimateReturn().compareTo(BigDecimal.ZERO) == 0) {
            tryStockEstimate(fundCode, detail);
        }

        // 持久化基金基本信息
        saveFundInfo(detail);

        return detail;
    }

    /**
     * 获取净值历史
     */
    @Cacheable(value = "navHistory", key = "#fundCode + '_' + #startDate + '_' + #endDate")
    public List<Map<String, Object>> getNavHistory(String fundCode, String startDate, String endDate) {
        return eastMoneyDataSource.getNavHistory(fundCode, startDate, endDate);
    }

    /**
     * 获取实时估值(多源验证+兜底)
     */
    @Cacheable(value = "estimateNav", key = "#fundCode", unless = "#result == null || #result.isEmpty()")
    public Map<String, Object> getEstimateNav(String fundCode) {
        // 主数据源估值
        Map<String, Object> estimate = eastMoneyDataSource.getEstimateNav(fundCode);

        if (estimate != null && !estimate.isEmpty()) {
            return estimate;
        }

        // 兜底: 股票估值
        log.info("使用股票兜底估值: {}", fundCode);
        Fund fund = fundMapper.selectById(fundCode);
        if (fund != null) {
            // 获取最新净值
            BigDecimal lastNav = getLatestNav(fundCode);
            if (lastNav != null && lastNav.compareTo(BigDecimal.ZERO) > 0) {
                return stockEstimateDataSource.estimateByStocks(fundCode, lastNav);
            }
        }

        return Collections.emptyMap();
    }

    /**
     * 获取基金最新净值
     */
    public BigDecimal getLatestNav(String fundCode) {
        // 1. 先从数据库查
        try {
            List<FundNav> navs = fundNavMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FundNav>()
                            .eq("fund_code", fundCode)
                            .orderByDesc("nav_date")
                            .last("LIMIT 1")
            );
            if (!navs.isEmpty()) {
                return navs.get(0).getNav();
            }
        } catch (Exception e) {
            log.warn("从数据库获取最新净值失败: {}", fundCode, e);
        }

        // 2. 数据库无数据时，从API获取最新一条净值
        try {
            List<java.util.Map<String, Object>> navList = eastMoneyDataSource.getNavHistory(fundCode, "", "");
            if (!navList.isEmpty()) {
                java.util.Map<String, Object> latest = navList.get(navList.size() - 1);
                BigDecimal nav = (BigDecimal) latest.get("nav");
                String navDate = (String) latest.get("navDate");
                // 同时写入数据库以便后续缓存
                if (nav != null && nav.compareTo(BigDecimal.ZERO) > 0 && navDate != null) {
                    try {
                        FundNav fundNav = new FundNav();
                        fundNav.setFundCode(fundCode);
                        fundNav.setNavDate(java.time.LocalDate.parse(navDate));
                        fundNav.setNav(nav);
                        fundNav.setAccNav((BigDecimal) latest.get("accNav"));
                        fundNav.setDailyReturn((BigDecimal) latest.get("dailyReturn"));
                        fundNavMapper.insert(fundNav);
                    } catch (Exception ignored) {
                        // 可能重复插入，忽略
                    }
                }
                return nav;
            }
        } catch (Exception e) {
            log.warn("从API获取最新净值失败: {}", fundCode, e);
        }

        return null;
    }

    private void tryStockEstimate(String fundCode, FundDetailDTO detail) {
        try {
            BigDecimal lastNav = detail.getLatestNav();
            if (lastNav == null || lastNav.compareTo(BigDecimal.ZERO) == 0) {
                lastNav = getLatestNav(fundCode);
            }
            if (lastNav != null && lastNav.compareTo(BigDecimal.ZERO) > 0) {
                Map<String, Object> stockEstimate = stockEstimateDataSource.estimateByStocks(fundCode, lastNav);
                if (!stockEstimate.isEmpty()) {
                    detail.setEstimateNav((BigDecimal) stockEstimate.get("estimateNav"));
                    detail.setEstimateReturn((BigDecimal) stockEstimate.get("estimateReturn"));
                }
            }
        } catch (Exception e) {
            log.warn("股票兜底估值失败: {}", fundCode, e);
        }
    }

    /**
     * 将外部获取的基金信息持久化到数据库
     */
    private void saveFundInfo(FundDetailDTO detail) {
        try {
            Fund fund = fundMapper.selectById(detail.getCode());
            if (fund == null) {
                fund = new Fund();
                fund.setCode(detail.getCode());
            }
            fund.setName(detail.getName());
            fund.setType(detail.getType());
            fund.setCompany(detail.getCompany());
            fund.setManager(detail.getManager());
            fund.setScale(detail.getScale());
            fund.setRiskLevel(detail.getRiskLevel());
            fund.setFeeRate(detail.getFeeRate());
            fund.setTopHoldings(detail.getTopHoldings());
            fund.setIndustryDist(detail.getIndustryDist());

            if (detail.getEstablishDate() != null) {
                try {
                    fund.setEstablishDate(java.time.LocalDate.parse(detail.getEstablishDate()));
                } catch (Exception ignored) {}
            }

            if (fundMapper.selectById(detail.getCode()) == null) {
                fundMapper.insert(fund);
            } else {
                fundMapper.updateById(fund);
            }
        } catch (Exception e) {
            log.warn("保存基金信息失败: {}", detail.getCode(), e);
        }
    }
}
