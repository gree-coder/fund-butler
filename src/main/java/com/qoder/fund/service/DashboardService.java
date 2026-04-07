package com.qoder.fund.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.qoder.fund.dto.DashboardDTO;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.dto.ProfitAnalysisDTO;
import com.qoder.fund.dto.ProfitTrendDTO;
import com.qoder.fund.entity.FundNav;
import com.qoder.fund.entity.FundTransaction;
import com.qoder.fund.entity.Position;
import com.qoder.fund.mapper.FundNavMapper;
import com.qoder.fund.mapper.PositionMapper;
import com.qoder.fund.mapper.TransactionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PositionService positionService;
    private final PositionMapper positionMapper;
    private final FundNavMapper fundNavMapper;
    private final TransactionMapper transactionMapper;

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

    /**
     * 获取收益分析数据（收益曲线+回撤分析）
     * 修复：正确计算历史收益，考虑交易记录
     */
    public ProfitAnalysisDTO getProfitAnalysis(int days) {
        ProfitAnalysisDTO dto = new ProfitAnalysisDTO();

        // 1. 获取所有持仓
        List<Position> positions = positionMapper.selectList(new QueryWrapper<>());
        if (positions.isEmpty()) {
            return dto;
        }

        // 2. 获取日期范围
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;

        // 3. 获取所有持仓基金的历史净值
        Set<String> fundCodes = positions.stream()
                .map(Position::getFundCode)
                .collect(Collectors.toSet());

        QueryWrapper<FundNav> navQuery = new QueryWrapper<>();
        navQuery.in("fund_code", fundCodes);
        navQuery.ge("nav_date", startDate.minusDays(7)); // 多获取几天用于查找最近净值
        navQuery.le("nav_date", endDate);
        navQuery.orderByAsc("nav_date");
        List<FundNav> allNavs = fundNavMapper.selectList(navQuery);

        // 4. 按基金代码分组
        Map<String, List<FundNav>> navByFund = allNavs.stream()
                .collect(Collectors.groupingBy(FundNav::getFundCode));

        // 5. 获取所有交易记录
        List<Long> positionIds = positions.stream()
                .map(Position::getId)
                .collect(Collectors.toList());
        QueryWrapper<FundTransaction> txQuery = new QueryWrapper<>();
        txQuery.in("position_id", positionIds);
        txQuery.orderByAsc("trade_date");
        List<FundTransaction> allTransactions = transactionMapper.selectList(txQuery);

        // 按持仓ID分组
        Map<Long, List<FundTransaction>> txByPosition = allTransactions.stream()
                .collect(Collectors.groupingBy(FundTransaction::getPositionId));

        // 6. 计算每日市值和收益
        List<String> dates = new ArrayList<>();
        List<BigDecimal> marketValues = new ArrayList<>();
        List<BigDecimal> dailyProfits = new ArrayList<>();
        BigDecimal prevMarketValue = null;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // 跳过周末（基金不交易）
            int dayOfWeek = date.getDayOfWeek().getValue();
            if (dayOfWeek > 5) {
                continue;
            }

            dates.add(date.format(fmt));
            BigDecimal dayMarketValue = BigDecimal.ZERO;
            BigDecimal dayNetBuy = BigDecimal.ZERO; // 当日净买入金额

            for (Position p : positions) {
                // 计算该日期的份额（根据交易记录）
                BigDecimal sharesOnDate = calculateSharesOnDate(p, date, txByPosition.get(p.getId()));
                if (sharesOnDate == null || sharesOnDate.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // 该日期还没有持仓
                }

                List<FundNav> fundNavs = navByFund.get(p.getFundCode());
                if (fundNavs == null) {
                    continue;
                }

                // 找到该日期或之前最近的净值
                FundNav nav = findNearestNav(fundNavs, date);
                if (nav != null && nav.getNav() != null) {
                    dayMarketValue = dayMarketValue.add(
                            sharesOnDate.multiply(nav.getNav()).setScale(2, RoundingMode.HALF_UP)
                    );
                }

                // 计算当日净买入金额
                dayNetBuy = dayNetBuy.add(calculateNetBuyOnDate(p.getId(), date, txByPosition.get(p.getId())));
            }

            marketValues.add(dayMarketValue);

            // 计算每日真实收益 = 市值变化 - 净买入金额
            if (prevMarketValue != null && prevMarketValue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal dailyProfit = dayMarketValue.subtract(prevMarketValue).subtract(dayNetBuy);
                dailyProfits.add(dailyProfit);
            } else {
                dailyProfits.add(BigDecimal.ZERO);
            }
            prevMarketValue = dayMarketValue;
        }

        dto.setDates(dates);
        dto.setMarketValues(marketValues);
        dto.setDailyProfits(dailyProfits);

        // 7. 计算累计收益
        if (!marketValues.isEmpty() && !positions.isEmpty()) {
            List<BigDecimal> cumulativeProfits = new ArrayList<>();
            BigDecimal cumulativeProfit = BigDecimal.ZERO;
            for (int i = 0; i < dailyProfits.size(); i++) {
                cumulativeProfit = cumulativeProfit.add(dailyProfits.get(i));
                cumulativeProfits.add(cumulativeProfit.setScale(2, RoundingMode.HALF_UP));
            }
            dto.setCumulativeProfits(cumulativeProfits);

            // 累计收益率 = 累计收益 / 累计投入本金
            List<BigDecimal> cumulativeReturns = new ArrayList<>();
            BigDecimal totalInvested = BigDecimal.ZERO;
            int dateIndex = 0;
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                int dayOfWeek = date.getDayOfWeek().getValue();
                if (dayOfWeek > 5) {
                    continue;
                }

                // 累计投入本金（截止到该日期）
                for (Position p : positions) {
                    totalInvested = totalInvested.add(
                            calculateInvestedAmountOnDate(p, date, txByPosition.get(p.getId()))
                    );
                }

                if (totalInvested.compareTo(BigDecimal.ZERO) > 0 && dateIndex < cumulativeProfits.size()) {
                    BigDecimal returnRate = cumulativeProfits.get(dateIndex)
                            .divide(totalInvested, 4, RoundingMode.HALF_UP)
                            .multiply(new BigDecimal("100"))
                            .setScale(2, RoundingMode.HALF_UP);
                    cumulativeReturns.add(returnRate);
                } else {
                    cumulativeReturns.add(BigDecimal.ZERO);
                }
                dateIndex++;
            }
            dto.setCumulativeReturns(cumulativeReturns);
        }

        // 8. 计算回撤
        dto.setDrawdown(calculateDrawdown(marketValues, dates));

        // 9. 计算性能指标
        dto.setMetrics(calculateMetrics(dailyProfits, marketValues, dates));

        return dto;
    }

    /**
     * 计算指定日期的持仓份额
     */
    private BigDecimal calculateSharesOnDate(Position position, LocalDate date,
            List<FundTransaction> transactions) {
        BigDecimal shares = BigDecimal.ZERO;

        if (transactions != null) {
            for (FundTransaction tx : transactions) {
                if (!tx.getTradeDate().isAfter(date)) {
                    if ("buy".equalsIgnoreCase(tx.getType()) && tx.getShares() != null) {
                        shares = shares.add(tx.getShares());
                    } else if ("sell".equalsIgnoreCase(tx.getType()) && tx.getShares() != null) {
                        shares = shares.subtract(tx.getShares());
                    }
                }
            }
        }

        // 如果没有交易记录或交易记录在该日期之后，检查持仓创建时间
        if (shares.compareTo(BigDecimal.ZERO) == 0) {
            // 如果持仓创建于该日期之前，使用当前份额（假设没有交易记录）
            if (position.getCreatedAt() != null
                    && !position.getCreatedAt().toLocalDate().isAfter(date)) {
                // 如果该日期之后有交易记录，说明份额变化了，不使用当前份额
                boolean hasLaterTx = transactions != null && transactions.stream()
                        .anyMatch(tx -> tx.getTradeDate().isAfter(date));
                if (!hasLaterTx) {
                    shares = position.getShares();
                }
            }
        }

        return shares.compareTo(BigDecimal.ZERO) > 0 ? shares : null;
    }

    /**
     * 计算指定日期的净买入金额（买入为正，卖出为负）
     */
    private BigDecimal calculateNetBuyOnDate(Long positionId, LocalDate date,
            List<FundTransaction> transactions) {
        BigDecimal netBuy = BigDecimal.ZERO;

        if (transactions != null) {
            for (FundTransaction tx : transactions) {
                if (tx.getTradeDate().equals(date)) {
                    if ("buy".equalsIgnoreCase(tx.getType()) && tx.getAmount() != null) {
                        netBuy = netBuy.add(tx.getAmount());
                    } else if ("sell".equalsIgnoreCase(tx.getType()) && tx.getAmount() != null) {
                        netBuy = netBuy.subtract(tx.getAmount());
                    }
                }
            }
        }

        return netBuy;
    }

    /**
     * 计算截止到指定日期的累计投入金额
     */
    private BigDecimal calculateInvestedAmountOnDate(Position position, LocalDate date,
            List<FundTransaction> transactions) {
        BigDecimal invested = BigDecimal.ZERO;

        if (transactions != null) {
            for (FundTransaction tx : transactions) {
                if (!tx.getTradeDate().isAfter(date)) {
                    if ("buy".equalsIgnoreCase(tx.getType()) && tx.getAmount() != null) {
                        invested = invested.add(tx.getAmount());
                    } else if ("sell".equalsIgnoreCase(tx.getType()) && tx.getAmount() != null) {
                        invested = invested.subtract(tx.getAmount());
                    }
                }
            }
        }

        // 如果没有交易记录，使用持仓的成本金额（假设持仓创建于该日期之前）
        if (invested.compareTo(BigDecimal.ZERO) == 0 && position.getCostAmount() != null) {
            if (position.getCreatedAt() != null
                    && !position.getCreatedAt().toLocalDate().isAfter(date)) {
                invested = position.getCostAmount();
            }
        }

        return invested;
    }

    /**
     * 查找指定日期或之前最近的净值记录
     */
    private FundNav findNearestNav(List<FundNav> navs, LocalDate date) {
        FundNav nearest = null;
        for (FundNav nav : navs) {
            if (!nav.getNavDate().isAfter(date)) {
                if (nearest == null || nav.getNavDate().isAfter(nearest.getNavDate())) {
                    nearest = nav;
                }
            }
        }
        return nearest;
    }

    /**
     * 计算回撤数据
     */
    private ProfitAnalysisDTO.DrawdownData calculateDrawdown(List<BigDecimal> marketValues, List<String> dates) {
        ProfitAnalysisDTO.DrawdownData drawdown = new ProfitAnalysisDTO.DrawdownData();

        if (marketValues.isEmpty()) {
            return drawdown;
        }

        BigDecimal peak = BigDecimal.ZERO;
        BigDecimal maxDrawdown = BigDecimal.ZERO;
        BigDecimal maxDrawdownAmount = BigDecimal.ZERO;
        int maxDrawdownStart = 0;
        int maxDrawdownEnd = 0;
        int currentPeakIndex = 0;

        List<BigDecimal> drawdownCurve = new ArrayList<>();

        for (int i = 0; i < marketValues.size(); i++) {
            BigDecimal mv = marketValues.get(i);
            if (mv.compareTo(peak) > 0) {
                peak = mv;
                currentPeakIndex = i;
            }

            // 计算当前回撤率
            BigDecimal currentDrawdown = BigDecimal.ZERO;
            if (peak.compareTo(BigDecimal.ZERO) > 0) {
                currentDrawdown = peak.subtract(mv)
                        .divide(peak, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"))
                        .setScale(2, RoundingMode.HALF_UP);
            }
            drawdownCurve.add(currentDrawdown.negate()); // 负值表示回撤

            // 检查是否为最大回撤
            if (currentDrawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = currentDrawdown;
                maxDrawdownAmount = peak.subtract(mv);
                maxDrawdownStart = currentPeakIndex;
                maxDrawdownEnd = i;
            }
        }

        drawdown.setMaxDrawdown(maxDrawdown);
        drawdown.setMaxDrawdownAmount(maxDrawdownAmount);
        if (maxDrawdown.compareTo(BigDecimal.ZERO) > 0) {
            drawdown.setStartDate(dates.get(maxDrawdownStart));
            drawdown.setEndDate(dates.get(maxDrawdownEnd));
            drawdown.setDuration(maxDrawdownEnd - maxDrawdownStart);
        } else {
            drawdown.setStartDate(null);
            drawdown.setEndDate(null);
            drawdown.setDuration(0);
        }
        drawdown.setDrawdownCurve(drawdownCurve);

        return drawdown;
    }

    /**
     * 计算性能指标
     */
    private ProfitAnalysisDTO.PerformanceMetrics calculateMetrics(
            List<BigDecimal> dailyProfits,
            List<BigDecimal> marketValues,
            List<String> dates) {

        ProfitAnalysisDTO.PerformanceMetrics metrics = new ProfitAnalysisDTO.PerformanceMetrics();

        if (dailyProfits.isEmpty() || marketValues.isEmpty()) {
            metrics.setTotalReturn(BigDecimal.ZERO);
            metrics.setAnnualizedReturn(BigDecimal.ZERO);
            metrics.setSharpeRatio(BigDecimal.ZERO);
            metrics.setVolatility(BigDecimal.ZERO);
            metrics.setProfitDays(0);
            metrics.setLossDays(0);
            metrics.setWinRate(BigDecimal.ZERO);
            return metrics;
        }

        // 总收益率
        BigDecimal firstValue = marketValues.get(0);
        BigDecimal lastValue = marketValues.get(marketValues.size() - 1);
        BigDecimal totalReturn = BigDecimal.ZERO;
        if (firstValue != null && firstValue.compareTo(BigDecimal.ZERO) > 0) {
            totalReturn = lastValue.subtract(firstValue)
                    .divide(firstValue, 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        metrics.setTotalReturn(totalReturn);

        // 年化收益率
        if (dates.size() > 1) {
            long days = ChronoUnit.DAYS.between(
                    LocalDate.parse(dates.get(0)),
                    LocalDate.parse(dates.get(dates.size() - 1))
            ) + 1;
            if (days > 0) {
                double years = days / 365.0;
                if (years > 0 && firstValue != null && firstValue.compareTo(BigDecimal.ZERO) > 0) {
                    double annualized = (Math.pow(lastValue.divide(firstValue, 6, RoundingMode.HALF_UP).doubleValue(),
                            1.0 / years) - 1) * 100;
                    metrics.setAnnualizedReturn(BigDecimal.valueOf(annualized).setScale(2, RoundingMode.HALF_UP));
                } else {
                    metrics.setAnnualizedReturn(BigDecimal.ZERO);
                }
            }
        }

        // 波动率（基于日收益率计算）
        List<BigDecimal> dailyReturns = new ArrayList<>();
        for (int i = 0; i < dailyProfits.size(); i++) {
            BigDecimal mv = marketValues.get(i);
            BigDecimal profit = dailyProfits.get(i);
            // 只有在有市值且市值大于0时才计算收益率
            if (mv != null && mv.compareTo(BigDecimal.ZERO) > 0 && profit != null) {
                BigDecimal ret = profit.divide(mv, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
                dailyReturns.add(ret);
            }
        }

        if (!dailyReturns.isEmpty()) {
            BigDecimal meanReturn = dailyReturns.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(dailyReturns.size()), 4, RoundingMode.HALF_UP);

            double variance = dailyReturns.stream()
                    .mapToDouble(r -> r.subtract(meanReturn).pow(2).doubleValue())
                    .average()
                    .orElse(0);

            double stdDev = Math.sqrt(variance);
            // 年化波动率 = 日波动率 * sqrt(252)
            double annualizedVol = stdDev * Math.sqrt(252);
            metrics.setVolatility(BigDecimal.valueOf(annualizedVol)
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            metrics.setVolatility(BigDecimal.ZERO);
        }

        // 胜率
        int profitDays = 0;
        int lossDays = 0;
        for (BigDecimal p : dailyProfits) {
            if (p.compareTo(BigDecimal.ZERO) > 0) profitDays++;
            else if (p.compareTo(BigDecimal.ZERO) < 0) lossDays++;
        }
        metrics.setProfitDays(profitDays);
        metrics.setLossDays(lossDays);
        int totalDays = profitDays + lossDays;
        if (totalDays > 0) {
            metrics.setWinRate(BigDecimal.valueOf(profitDays * 100.0 / totalDays)
                    .setScale(2, RoundingMode.HALF_UP));
        } else {
            metrics.setWinRate(BigDecimal.ZERO);
        }

        // 简化夏普比率 = (年化收益率 - 3%) / 年化波动率
        if (metrics.getVolatility() != null && metrics.getVolatility().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal sharpe = metrics.getAnnualizedReturn()
                    .subtract(new BigDecimal("3"))
                    .divide(metrics.getVolatility(), 2, RoundingMode.HALF_UP);
            metrics.setSharpeRatio(sharpe);
        } else {
            metrics.setSharpeRatio(BigDecimal.ZERO);
        }

        return metrics;
    }
}
