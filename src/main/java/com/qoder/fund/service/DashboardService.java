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
import java.util.List;

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

        for (PositionDTO p : positions) {
            if (p.getMarketValue() != null) {
                totalAsset = totalAsset.add(p.getMarketValue());
            }
            if (p.getCostAmount() != null) {
                totalCost = totalCost.add(p.getCostAmount());
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
        }

        dto.setTotalAsset(totalAsset.setScale(2, RoundingMode.HALF_UP));
        BigDecimal totalProfit = totalAsset.subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
        dto.setTotalProfit(totalProfit);
        dto.setTodayProfit(todayProfit);

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
