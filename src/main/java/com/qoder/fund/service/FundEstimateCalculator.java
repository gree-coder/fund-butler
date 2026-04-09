package com.qoder.fund.service;

import com.qoder.fund.dto.EstimateSourceDTO;
import com.qoder.fund.datasource.EastMoneyDataSource;
import com.qoder.fund.datasource.SinaDataSource;
import com.qoder.fund.datasource.StockEstimateDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/**
 * 基金估值计算器
 * 负责多数据源估值聚合和智能估算
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundEstimateCalculator {

    private final EastMoneyDataSource eastMoneyDataSource;
    private final SinaDataSource sinaDataSource;
    private final StockEstimateDataSource stockEstimateDataSource;

    /**
     * 计算智能综合估值
     */
    public EstimateSourceDTO.EstimateItem calculateSmartEstimate(
            String fundCode,
            List<EstimateSourceDTO.EstimateItem> sources,
            BigDecimal lastNav,
            String fundType) {

        EstimateSourceDTO.EstimateItem smartItem = new EstimateSourceDTO.EstimateItem();
        smartItem.setKey("smart");
        smartItem.setLabel("智能综合预估");
        smartItem.setDescription("基于历史准确度加权的多源综合估算");

        // 收集有效数据源
        List<EstimateSourceDTO.EstimateItem> validSources = sources.stream()
                .filter(s -> s.isAvailable()
                        && s.getEstimateNav() != null
                        && !"actual".equals(s.getKey())
                        && !"smart".equals(s.getKey()))
                .toList();

        if (validSources.isEmpty() || lastNav == null) {
            smartItem.setAvailable(false);
            return smartItem;
        }

        // 计算加权平均
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;

        for (EstimateSourceDTO.EstimateItem source : validSources) {
            BigDecimal weight = getSourceWeight(source.getKey(), fundType);
            totalWeight = totalWeight.add(weight);
            weightedSum = weightedSum.add(source.getEstimateReturn().multiply(weight));
        }

        if (totalWeight.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal avgReturn = weightedSum.divide(totalWeight, 4, RoundingMode.HALF_UP);
            BigDecimal estimateNav = lastNav.multiply(
                    BigDecimal.ONE.add(avgReturn.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP))
            ).setScale(4, RoundingMode.HALF_UP);

            smartItem.setEstimateNav(estimateNav);
            smartItem.setEstimateReturn(avgReturn);
            smartItem.setAvailable(true);
        } else {
            smartItem.setAvailable(false);
        }

        return smartItem;
    }

    /**
     * 获取数据源权重（基于历史准确度）
     */
    private BigDecimal getSourceWeight(String sourceKey, String fundType) {
        // QDII基金：股票估值权重更高
        if ("QDII".equals(fundType)) {
            return switch (sourceKey) {
                case "stock" -> new BigDecimal("0.45");
                case "eastmoney" -> new BigDecimal("0.35");
                case "sina" -> new BigDecimal("0.20");
                default -> new BigDecimal("0.33");
            };
        }

        // 默认权重
        return switch (sourceKey) {
            case "eastmoney" -> new BigDecimal("0.45");
            case "sina" -> new BigDecimal("0.30");
            case "stock" -> new BigDecimal("0.25");
            default -> new BigDecimal("0.33");
        };
    }

    /**
     * 尝试使用股票估值兜底
     */
    public boolean tryStockEstimate(String fundCode, BigDecimal lastNav, EstimateSourceDTO.EstimateItem targetItem) {
        if (lastNav == null || lastNav.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        try {
            Map<String, Object> stockEstimate = stockEstimateDataSource.estimateByStocks(fundCode, lastNav);
            if (stockEstimate != null && !stockEstimate.isEmpty() && stockEstimate.get("estimateNav") != null) {
                targetItem.setEstimateNav((BigDecimal) stockEstimate.get("estimateNav"));
                targetItem.setEstimateReturn((BigDecimal) stockEstimate.get("estimateReturn"));
                targetItem.setAvailable(true);

                // ETF使用实时价格时更新标签
                if ("etf_realtime".equals(stockEstimate.get("source"))) {
                    targetItem.setLabel("ETF实时价格");
                    targetItem.setDescription("基于ETF二级市场实时交易价格");
                }
                return true;
            }
        } catch (Exception e) {
            log.warn("股票估值兜底失败: {}", fundCode, e);
        }
        return false;
    }
}
