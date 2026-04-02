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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

        // 构建各数据源估值列表(排除 actual)
        List<EstimateAnalysisDTO.SourceEstimate> sources = new ArrayList<>();
        EstimateSourceDTO.EstimateItem smartItem = null;

        for (EstimateSourceDTO.EstimateItem item : sourceDTO.getSources()) {
            if ("smart".equals(item.getKey())) {
                smartItem = item;
                continue;
            }
            if ("actual".equals(item.getKey())) {
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
     */
    private List<EstimateAnalysisDTO.CompensationLog> buildCompensationLogs(String fundCode) {
        List<EstimateAnalysisDTO.CompensationLog> logs = new ArrayList<>();

        // 获取最近7天的净值记录，检查是否有更新
        LocalDate startDate = LocalDate.now().minusDays(7);
        List<FundNav> navs = fundNavMapper.selectList(
                new QueryWrapper<FundNav>()
                        .eq("fund_code", fundCode)
                        .ge("nav_date", startDate)
                        .orderByDesc("nav_date")
        );

        // 获取预测记录
        List<EstimatePrediction> predictions = estimatePredictionMapper.selectList(
                new QueryWrapper<EstimatePrediction>()
                        .eq("fund_code", fundCode)
                        .ge("predict_date", startDate)
                        .isNotNull("actual_return")
                        .orderByDesc("predict_date")
        );

        // 按日期分组处理
        Map<LocalDate, List<EstimatePrediction>> predByDate = predictions.stream()
                .collect(Collectors.groupingBy(EstimatePrediction::getPredictDate));

        for (FundNav nav : navs) {
            LocalDate date = nav.getNavDate();
            List<EstimatePrediction> dayPreds = predByDate.get(date);

            if (dayPreds != null && !dayPreds.isEmpty()) {
                // 有预测记录，说明是预测数据补偿
                EstimatePrediction pred = dayPreds.get(0);

                EstimateAnalysisDTO.CompensationLog log = new EstimateAnalysisDTO.CompensationLog();
                log.setDate(date);
                log.setBeforeNav(pred.getPredictedNav());
                log.setBeforeReturn(pred.getPredictedReturn());
                log.setAfterNav(nav.getNav());
                log.setAfterReturn(nav.getDailyReturn());
                log.setSource(pred.getSourceKey());
                log.setType(EstimateAnalysisDTO.CompensationType.PREDICT);
                log.setReason("预测数据补偿: " + getSourceLabel(pred.getSourceKey()));
                logs.add(log);
            } else {
                // 无预测记录，说明是实际净值直接录入
                EstimateAnalysisDTO.CompensationLog log = new EstimateAnalysisDTO.CompensationLog();
                log.setDate(date);
                log.setAfterNav(nav.getNav());
                log.setAfterReturn(nav.getDailyReturn());
                log.setSource("official");
                log.setType(EstimateAnalysisDTO.CompensationType.ACTUAL);
                log.setReason("基金公司官方净值发布");
                logs.add(log);
            }
        }

        return logs.stream()
                .sorted(Comparator.comparing(EstimateAnalysisDTO.CompensationLog::getDate).reversed())
                .collect(Collectors.toList());
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
