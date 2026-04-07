package com.qoder.fund.service;

import com.qoder.fund.dto.DashboardDTO;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.dto.ProfitTrendDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PositionService positionService;

    public DashboardDTO getDashboard() {
        List<PositionDTO> positions = positionService.list(null);

        DashboardDTO dto = new DashboardDTO();
        dto.setPositions(positions);

        BigDecimal totalAsset = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal todayProfit = BigDecimal.ZERO;
        BigDecimal todayEstimateProfit = BigDecimal.ZERO;
        boolean hasActualReturn = false; // 是否有任何持仓有实际涨幅

        // 行业分布聚合
        Map<String, BigDecimal> industryMarketValue = new HashMap<>();

        for (PositionDTO p : positions) {
            if (p.getMarketValue() != null) {
                totalAsset = totalAsset.add(p.getMarketValue());
            }
            if (p.getCostAmount() != null) {
                totalCost = totalCost.add(p.getCostAmount());
            }

            // 计算今日预估收益金额 = 持仓市值 × 今日预估涨幅 / 100
            if (p.getMarketValue() != null && p.getEstimateReturn() != null) {
                BigDecimal estimateProfit = p.getMarketValue()
                        .multiply(p.getEstimateReturn())
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                p.setEstimateProfit(estimateProfit);
                todayEstimateProfit = todayEstimateProfit.add(estimateProfit);
            }

            // 今日收益 = 份额 × 净值 × 日涨跌幅 / 100（优先使用实际涨幅）
            BigDecimal dailyReturn = p.getActualReturn() != null ? p.getActualReturn() : p.getEstimateReturn();
            if (p.getShares() != null && p.getLatestNav() != null && dailyReturn != null) {
                BigDecimal dailyProfit = p.getShares()
                        .multiply(p.getLatestNav())
                        .multiply(dailyReturn)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                todayProfit = todayProfit.add(dailyProfit);
            }

            // 检查是否有实际涨幅
            if (p.getActualReturn() != null) {
                hasActualReturn = true;
            }

            // 聚合行业分布：按市值加权
            if (p.getMarketValue() != null && p.getIndustryDist() != null) {
                for (Map<String, Object> item : p.getIndustryDist()) {
                    String industry = (String) item.get("industry");
                    Object ratioObj = item.get("ratio");
                    BigDecimal ratio = BigDecimal.ZERO;
                    if (ratioObj instanceof Number) {
                        ratio = new BigDecimal(ratioObj.toString());
                    }
                    // 该持仓中该行业的市值 = 持仓市值 × 行业比例 / 100
                    BigDecimal indMarketValue = p.getMarketValue()
                            .multiply(ratio)
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                    industryMarketValue.merge(industry, indMarketValue, BigDecimal::add);
                }
            }
        }

        // 构建行业分布列表
        List<Map<String, Object>> industryDistribution = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : industryMarketValue.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("industry", entry.getKey());
            item.put("marketValue", entry.getValue());
            // 计算行业占比
            if (totalAsset.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratio = entry.getValue()
                        .divide(totalAsset, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
                item.put("ratio", ratio);
            } else {
                item.put("ratio", BigDecimal.ZERO);
            }
            industryDistribution.add(item);
        }
        // 按市值降序排序
        industryDistribution.sort((a, b) -> {
            BigDecimal va = (BigDecimal) a.get("marketValue");
            BigDecimal vb = (BigDecimal) b.get("marketValue");
            return vb.compareTo(va);
        });
        dto.setIndustryDistribution(industryDistribution);

        dto.setTotalAsset(totalAsset.setScale(2, RoundingMode.HALF_UP));
        BigDecimal totalProfit = totalAsset.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
        dto.setTotalProfit(totalProfit);
        dto.setTodayProfit(todayProfit);
        dto.setTodayEstimateProfit(todayEstimateProfit);
        // 如果没有任何持仓有实际涨幅，则今日收益为预估值
        dto.setTodayProfitIsEstimate(!hasActualReturn);

        // 计算今日预估涨幅 = 今日预估收益 / 总资产 * 100
        if (totalAsset.compareTo(BigDecimal.ZERO) > 0) {
            dto.setTodayEstimateReturn(todayEstimateProfit
                    .divide(totalAsset, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            dto.setTodayEstimateReturn(BigDecimal.ZERO);
        }

        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            dto.setTotalProfitRate(totalProfit.divide(totalCost, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP));
        } else {
            dto.setTotalProfitRate(BigDecimal.ZERO);
        }

        return dto;
    }

    public ProfitTrendDTO getProfitTrend(int days) {
        ProfitTrendDTO dto = new ProfitTrendDTO();
        List<String> dates = new ArrayList<>();
        List<BigDecimal> profits = new ArrayList<>();

        // 生成日期列表 (简化实现：返回占位数据，真实数据需要每日净值记录)
        LocalDate today = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            dates.add(date.format(fmt));
            profits.add(BigDecimal.ZERO);
        }

        dto.setDates(dates);
        dto.setProfits(profits);
        return dto;
    }
}
