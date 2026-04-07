package com.qoder.fund.service;

import com.qoder.fund.dto.EstimateAnalysisDTO;
import com.qoder.fund.dto.EstimateSourceDTO;
import com.qoder.fund.datasource.FundDataAggregator;
import com.qoder.fund.entity.EstimatePrediction;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.entity.FundNav;
import com.qoder.fund.mapper.EstimatePredictionMapper;
import com.qoder.fund.mapper.FundMapper;
import com.qoder.fund.mapper.FundNavMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 数据源准确度分析服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstimateAnalysisService {

    private final FundDataAggregator fundDataAggregator;
    private final EstimatePredictionMapper estimatePredictionMapper;
    private final FundMapper fundMapper;
    private final FundNavMapper fundNavMapper;

    /**
     * 获取基金的数据源准确度分析
     */
    public EstimateAnalysisDTO getEstimateAnalysis(String fundCode) {
        EstimateAnalysisDTO dto = new EstimateAnalysisDTO();
        dto.setFundCode(fundCode);

        // 获取基金名称
        Fund fund = fundMapper.selectById(fundCode);
        if (fund != null) {
            dto.setFundName(fund.getName());
        }

        // 1. 实时估值数据
        dto.setCurrentEstimates(buildCurrentEstimates(fundCode));

        // 2. 准确度统计
        dto.setAccuracyStats(buildAccuracyStats(fundCode));

        // 3. 数据补偿记录
        dto.setCompensationLogs(buildCompensationLogs(fundCode));

        return dto;
    }

    /**
     * 构建实时估值数据
     */
    private EstimateAnalysisDTO.CurrentEstimate buildCurrentEstimates(String fundCode) {
        EstimateAnalysisDTO.CurrentEstimate current = new EstimateAnalysisDTO.CurrentEstimate();

        // 获取多数据源估值
        EstimateSourceDTO sourceDTO = fundDataAggregator.getMultiSourceEstimates(fundCode);

        // 查找实际净值数据
        EstimateSourceDTO.EstimateItem actualItem = sourceDTO.getSources().stream()
                .filter(s -> "actual".equals(s.getKey()))
                .findFirst()
                .orElse(null);

        if (actualItem != null && actualItem.isAvailable()) {
            current.setActualNav(actualItem.getEstimateNav());
            current.setActualReturn(actualItem.getEstimateReturn());
            current.setActualNavDate(actualItem.getDelayedDate() != null
                    ? actualItem.getDelayedDate()
                    : LocalDate.now());
            current.setActualReturnDelayed(actualItem.isDelayed());
        }

        // 先查找 smart 数据源（权重从中获取）
        EstimateSourceDTO.EstimateItem smartItem = sourceDTO.getSources().stream()
                .filter(s -> "smart".equals(s.getKey()))
                .findFirst()
                .orElse(null);

        // 构建各数据源估值列表(排除 actual 和 smart)
        List<EstimateAnalysisDTO.SourceEstimate> sources = new ArrayList<>();

        for (EstimateSourceDTO.EstimateItem item : sourceDTO.getSources()) {
            if ("smart".equals(item.getKey()) || "actual".equals(item.getKey())) {
                continue;
            }

            EstimateAnalysisDTO.SourceEstimate se = new EstimateAnalysisDTO.SourceEstimate();
            se.setKey(item.getKey());
            se.setLabel(item.getLabel());
            se.setEstimateNav(item.getEstimateNav());
            se.setEstimateReturn(item.getEstimateReturn());
            se.setAvailable(item.isAvailable());
            se.setDescription(item.getDescription());

            // 计算可信度(基于历史MAE)
            BigDecimal confidence = calculateConfidence(fundCode, item.getKey());
            se.setConfidence(confidence);

            // 权重(从smartItem中获取)
            if (smartItem != null && smartItem.getWeights() != null) {
                BigDecimal weight = smartItem.getWeights().get(item.getKey());
                se.setWeight(weight != null ? weight : BigDecimal.ZERO);
            } else {
                se.setWeight(BigDecimal.ZERO);
            }

            sources.add(se);
        }
        current.setSources(sources);

        // 智能综合预估
        if (smartItem != null) {
            EstimateAnalysisDTO.SmartEstimate smart = new EstimateAnalysisDTO.SmartEstimate();
            smart.setNav(smartItem.getEstimateNav());
            smart.setReturnRate(smartItem.getEstimateReturn());
            smart.setStrategy(smartItem.getStrategyType());
            smart.setScenario(smartItem.getScenario());
            smart.setAccuracyEnhanced(smartItem.isAccuracyEnhanced());
            smart.setWeights(smartItem.getWeights());
            smart.setDescription(smartItem.getDescription());
            current.setSmartEstimate(smart);
        }

        return current;
    }

    /**
     * 计算数据源可信度(基于历史MAE)
     */
    private BigDecimal calculateConfidence(String fundCode, String sourceKey) {
        LocalDate startDate = LocalDate.now().minusDays(30);
        BigDecimal mae = estimatePredictionMapper.getMaeInPeriod(
                fundCode, sourceKey, startDate, LocalDate.now());

        if (mae == null) {
            return new BigDecimal("0.5"); // 无历史数据时默认0.5
        }

        // MAE越小可信度越高: confidence = 1 / (1 + MAE)
        // MAE=0 -> 1.0, MAE=0.5 -> 0.67, MAE=1.0 -> 0.5, MAE=2.0 -> 0.33
        return BigDecimal.ONE.divide(BigDecimal.ONE.add(mae), 2, RoundingMode.HALF_UP);
    }

    /**
     * 构建准确度统计
     */
    private EstimateAnalysisDTO.AccuracyStats buildAccuracyStats(String fundCode) {
        EstimateAnalysisDTO.AccuracyStats stats = new EstimateAnalysisDTO.AccuracyStats();
        stats.setPeriod("30d");

        LocalDate startDate = LocalDate.now().minusDays(30);
        List<Map<String, Object>> rawStats = estimatePredictionMapper.getAccuracyStats(fundCode, startDate);

        List<EstimateAnalysisDTO.SourceAccuracy> sources = new ArrayList<>();
        for (Map<String, Object> raw : rawStats) {
            EstimateAnalysisDTO.SourceAccuracy sa = new EstimateAnalysisDTO.SourceAccuracy();
            sa.setKey((String) raw.get("sourceKey"));
            sa.setLabel(getSourceLabel((String) raw.get("sourceKey")));

            BigDecimal mae = (BigDecimal) raw.get("mae");
            sa.setMae(mae != null ? mae : BigDecimal.ZERO);

            Long count = ((Number) raw.get("predictionCount")).longValue();
            sa.setPredictionCount(count.intValue());

            BigDecimal hitRate = (BigDecimal) raw.get("hitRate");
            sa.setHitRate(hitRate != null ? hitRate.multiply(new BigDecimal("100")) : BigDecimal.ZERO);

            // 计算星级评级(基于MAE)
            sa.setRating(calculateRating(mae));

            // 判断趋势(对比最近7天和7-14天的MAE)
            sa.setTrend(calculateTrend(fundCode, sa.getKey()));

            sources.add(sa);
        }

        stats.setSources(sources);
        return stats;
    }

    /**
     * 计算星级评级
     */
    private Integer calculateRating(BigDecimal mae) {
        if (mae == null) return 3;
        double maeVal = mae.doubleValue();
        if (maeVal < 0.1) return 5;
        if (maeVal < 0.2) return 4;
        if (maeVal < 0.4) return 3;
        if (maeVal < 0.7) return 2;
        return 1;
    }

    /**
     * 计算趋势
     */
    private String calculateTrend(String fundCode, String sourceKey) {
        LocalDate now = LocalDate.now();
        BigDecimal maeRecent = estimatePredictionMapper.getMaeInPeriod(
                fundCode, sourceKey, now.minusDays(7), now);
        BigDecimal maeBefore = estimatePredictionMapper.getMaeInPeriod(
                fundCode, sourceKey, now.minusDays(14), now.minusDays(7));

        if (maeRecent == null || maeBefore == null) {
            return "stable";
        }

        double diff = maeBefore.doubleValue() - maeRecent.doubleValue();
        if (diff > 0.05) return "improving";  // 误差减小，趋势变好
        if (diff < -0.05) return "declining"; // 误差增大，趋势变差
        return "stable";
    }

    /**
     * 构建数据补偿记录
     * 显示每个数据源的预估与实际净值对比
     * 包含：已有净值的日期（预估vs实际） + 今日预估（待验证）
     */
    private List<EstimateAnalysisDTO.CompensationLog> buildCompensationLogs(String fundCode) {
        List<EstimateAnalysisDTO.CompensationLog> logs = new ArrayList<>();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(7);

        // 获取最近7天的净值记录
        List<FundNav> navs = fundNavMapper.selectList(
                new QueryWrapper<FundNav>()
                        .eq("fund_code", fundCode)
                        .ge("nav_date", startDate)
                        .orderByDesc("nav_date")
        );

        // 获取预测记录（各数据源）
        List<EstimatePrediction> predictions = estimatePredictionMapper.selectList(
                new QueryWrapper<EstimatePrediction>()
                        .eq("fund_code", fundCode)
                        .ge("predict_date", startDate)
                        .orderByDesc("predict_date")
        );

        // 按日期分组
        Map<LocalDate, List<EstimatePrediction>> predByDate = predictions.stream()
                .collect(Collectors.groupingBy(EstimatePrediction::getPredictDate));

        // 已有净值的日期
        Set<LocalDate> navDates = navs.stream()
                .map(FundNav::getNavDate)
                .collect(Collectors.toSet());

        // 按数据源分组（用于确定显示顺序）
        List<String> sourceOrder = List.of("smart", "eastmoney", "sina", "tencent", "stock");

        // 1. 处理已有净值的日期（预估 vs 实际）
        for (FundNav nav : navs) {
            LocalDate date = nav.getNavDate();
            List<EstimatePrediction> dayPreds = predByDate.get(date);

            if (dayPreds != null && !dayPreds.isEmpty()) {
                // 有预测记录，按数据源分组显示
                Map<String, List<EstimatePrediction>> predBySource = dayPreds.stream()
                        .collect(Collectors.groupingBy(EstimatePrediction::getSourceKey));

                // 按预设顺序显示各数据源
                for (String sourceKey : sourceOrder) {
                    List<EstimatePrediction> sourcePreds = predBySource.get(sourceKey);
                    if (sourcePreds != null && !sourcePreds.isEmpty()) {
                        EstimatePrediction pred = sourcePreds.get(0);
                        EstimateAnalysisDTO.CompensationLog log = buildCompensationLog(date, pred, nav);
                        logs.add(log);
                    }
                }

                // 显示其他未在预设顺序中的数据源
                for (Map.Entry<String, List<EstimatePrediction>> entry : predBySource.entrySet()) {
                    if (!sourceOrder.contains(entry.getKey())) {
                        EstimatePrediction pred = entry.getValue().get(0);
                        EstimateAnalysisDTO.CompensationLog log = buildCompensationLog(date, pred, nav);
                        logs.add(log);
                    }
                }
            } else {
                // 无预测记录，显示官方净值发布
                EstimateAnalysisDTO.CompensationLog log = new EstimateAnalysisDTO.CompensationLog();
                log.setDate(date);
                log.setAfterNav(nav.getNav());
                log.setAfterReturn(nav.getDailyReturn());
                log.setSource("official");
                log.setType(EstimateAnalysisDTO.CompensationType.ACTUAL);
                log.setCompensatedAt(LocalDateTime.of(date, LocalTime.of(20, 0))); // 基金净值通常晚上20:00公布
                log.setReason("基金公司官方净值发布");
                logs.add(log);
            }
        }

        // 2. 处理今日预估（还没有实际净值的预测记录）
        List<EstimatePrediction> todayPreds = predByDate.get(today);
        if (todayPreds != null && !todayPreds.isEmpty() && !navDates.contains(today)) {
            Map<String, List<EstimatePrediction>> predBySource = todayPreds.stream()
                    .collect(Collectors.groupingBy(EstimatePrediction::getSourceKey));

            for (String sourceKey : sourceOrder) {
                List<EstimatePrediction> sourcePreds = predBySource.get(sourceKey);
                if (sourcePreds != null && !sourcePreds.isEmpty()) {
                    EstimatePrediction pred = sourcePreds.get(0);
                    EstimateAnalysisDTO.CompensationLog log = new EstimateAnalysisDTO.CompensationLog();
                    log.setDate(today);
                    // 补偿前为空，补偿后为预测值
                    log.setAfterNav(pred.getPredictedNav());
                    log.setAfterReturn(pred.getPredictedReturn());
                    log.setSource(pred.getSourceKey());
                    log.setType(EstimateAnalysisDTO.CompensationType.PREDICT);
                    log.setCompensatedAt(LocalDateTime.now()); // 快照时间
                    log.setReason(getSourceLabel(pred.getSourceKey()) + "预估（待验证）");
                    logs.add(log);
                }
            }

            // 显示其他未在预设顺序中的数据源
            for (Map.Entry<String, List<EstimatePrediction>> entry : predBySource.entrySet()) {
                if (!sourceOrder.contains(entry.getKey())) {
                    EstimatePrediction pred = entry.getValue().get(0);
                    EstimateAnalysisDTO.CompensationLog log = new EstimateAnalysisDTO.CompensationLog();
                    log.setDate(today);
                    // 补偿前为空，补偿后为预测值
                    log.setAfterNav(pred.getPredictedNav());
                    log.setAfterReturn(pred.getPredictedReturn());
                    log.setSource(pred.getSourceKey());
                    log.setType(EstimateAnalysisDTO.CompensationType.PREDICT);
                    log.setCompensatedAt(LocalDateTime.now()); // 快照时间
                    log.setReason(getSourceLabel(pred.getSourceKey()) + "预估（待验证）");
                    logs.add(log);
                }
            }
        }

        return logs.stream()
                .sorted(Comparator.comparing(EstimateAnalysisDTO.CompensationLog::getDate).reversed()
                        .thenComparing(log -> sourceOrder.indexOf(log.getSource())))
                .collect(Collectors.toList());
    }

    /**
     * 构建单条补偿记录
     */
    private EstimateAnalysisDTO.CompensationLog buildCompensationLog(LocalDate date, EstimatePrediction pred, FundNav nav) {
        EstimateAnalysisDTO.CompensationLog log = new EstimateAnalysisDTO.CompensationLog();
        log.setDate(date);
        log.setBeforeNav(pred.getPredictedNav());
        log.setBeforeReturn(pred.getPredictedReturn());
        log.setAfterNav(nav.getNav());
        log.setAfterReturn(nav.getDailyReturn());
        log.setSource(pred.getSourceKey());
        // 补偿时间：净值公布时间（通常晚上20:00）
        log.setCompensatedAt(LocalDateTime.of(date, LocalTime.of(20, 0)));

        // 判断是否有实际值进行对比
        if (pred.getActualReturn() != null) {
            log.setType(EstimateAnalysisDTO.CompensationType.PREDICT);
            // 计算误差
            BigDecimal navError = pred.getReturnError() != null ? pred.getReturnError() : BigDecimal.ZERO;
            String errorSign = navError.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            log.setReason(String.format("%s预估 vs 实际 (%s%.2f%%)",
                    getSourceLabel(pred.getSourceKey()), errorSign, navError));
        } else {
            // 无实际值对比，仅记录预估
            log.setType(EstimateAnalysisDTO.CompensationType.PREDICT);
            log.setReason(getSourceLabel(pred.getSourceKey()) + "预估（待验证）");
        }

        return log;
    }

    private String getSourceLabel(String key) {
        return switch (key) {
            case "eastmoney" -> "天天基金";
            case "sina" -> "新浪财经";
            case "tencent" -> "腾讯财经";
            case "stock" -> "重仓股估算";
            case "smart" -> "智能综合";
            case "official" -> "官方净值";
            default -> key;
        };
    }
}
