package com.qoder.fund.service;

import com.qoder.fund.entity.EstimatePrediction;
import com.qoder.fund.mapper.EstimatePredictionMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 估值权重计算服务
 * 负责自适应权重计算和历史准确度修正
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EstimateWeightService {

    private final EstimatePredictionMapper estimatePredictionMapper;

    // 场景权重配置
    private static final Map<String, Map<String, BigDecimal>> SCENARIO_WEIGHTS = Map.of(
            // ETF实时价格场景
            "ETF实时", Map.of(
                    "eastmoney", new BigDecimal("0.15"),
                    "sina", new BigDecimal("0.10"),
                    "stock", new BigDecimal("0.75")
            ),
            // 固收类场景
            "固收类", Map.of(
                    "eastmoney", new BigDecimal("0.55"),
                    "sina", new BigDecimal("0.35"),
                    "stock", new BigDecimal("0.10")
            ),
            // QDII场景
            "QDII", Map.of(
                    "eastmoney", new BigDecimal("0.50"),
                    "sina", new BigDecimal("0.35"),
                    "stock", new BigDecimal("0.15")
            ),
            // 权益高覆盖场景
            "权益高覆盖", Map.of(
                    "eastmoney", new BigDecimal("0.35"),
                    "sina", new BigDecimal("0.20"),
                    "stock", new BigDecimal("0.45")
            ),
            // 权益中覆盖场景
            "权益中覆盖", Map.of(
                    "eastmoney", new BigDecimal("0.45"),
                    "sina", new BigDecimal("0.30"),
                    "stock", new BigDecimal("0.25")
            ),
            // 权益低覆盖场景
            "权益低覆盖", Map.of(
                    "eastmoney", new BigDecimal("0.55"),
                    "sina", new BigDecimal("0.35"),
                    "stock", new BigDecimal("0.10")
            )
    );

    /**
     * 权重计算结果
     */
    public record WeightResult(
            Map<String, BigDecimal> weights,
            String scenario,
            boolean accuracyEnhanced,
            boolean coldStart  // 是否为冷启动（无历史数据）
    ) {}

    /**
     * 确定自适应权重
     *
     * @param fundType       基金类型
     * @param stockSourceType 股票数据源类型
     * @param coverageRatio  持仓覆盖率
     * @return 权重计算结果
     */
    public WeightResult determineAdaptiveWeights(String fundType,
                                                  String stockSourceType,
                                                  BigDecimal coverageRatio) {
        String scenario;
        Map<String, BigDecimal> weights;

        // 场景A: ETF实时价格
        if ("etf_realtime".equals(stockSourceType)) {
            scenario = "ETF实时";
            weights = SCENARIO_WEIGHTS.get("ETF实时");
        } else if ("BOND".equals(fundType) || "MONEY".equals(fundType)) {
            // 场景B: 债券/货币基金
            scenario = "固收类";
            weights = SCENARIO_WEIGHTS.get("固收类");
        } else if ("QDII".equals(fundType)) {
            // 场景C: QDII
            scenario = "QDII";
            weights = SCENARIO_WEIGHTS.get("QDII");
        } else {
            // 场景D/E/F: 权益类按覆盖率分档
            BigDecimal cov = coverageRatio != null ? coverageRatio : BigDecimal.ZERO;
            if (cov.compareTo(new BigDecimal("60")) >= 0) {
                scenario = "权益高覆盖";
                weights = SCENARIO_WEIGHTS.get("权益高覆盖");
            } else if (cov.compareTo(new BigDecimal("30")) >= 0) {
                scenario = "权益中覆盖";
                weights = SCENARIO_WEIGHTS.get("权益中覆盖");
            } else {
                scenario = "权益低覆盖";
                weights = SCENARIO_WEIGHTS.get("权益低覆盖");
            }
        }

        log.info("自适应权重: type={}, stockSource={}, coverage={}%, 场景={}",
                fundType, stockSourceType,
                coverageRatio != null ? coverageRatio.setScale(1, RoundingMode.HALF_UP) : "N/A",
                scenario);

        return new WeightResult(weights, scenario, false, false);
    }

    /**
     * 确定新基金的保守权重（冷启动保护）
     * 新基金前5个交易日使用单一可靠源，不展示智能综合
     *
     * @param fundType       基金类型
     * @param stockSourceType 股票数据源类型
     * @return 保守权重结果
     */
    public WeightResult determineConservativeWeights(String fundType, String stockSourceType) {
        Map<String, BigDecimal> weights;
        String scenario;

        // ETF基金：使用ETF实时价格（如果有）
        if ("etf_realtime".equals(stockSourceType)) {
            weights = Map.of(
                    "eastmoney", new BigDecimal("0.10"),
                    "sina", new BigDecimal("0.10"),
                    "stock", new BigDecimal("0.80")
            );
            scenario = "ETF实时(新基金)";
        } else if ("BOND".equals(fundType) || "MONEY".equals(fundType)) {
            // 固收类：以天天基金为主
            weights = Map.of(
                    "eastmoney", new BigDecimal("0.75"),
                    "sina", new BigDecimal("0.25"),
                    "stock", new BigDecimal("0.00")
            );
            scenario = "固收类(新基金)";
        } else if ("QDII".equals(fundType)) {
            // QDII：以机构估值为主，不信任股票估算
            weights = Map.of(
                    "eastmoney", new BigDecimal("0.60"),
                    "sina", new BigDecimal("0.40"),
                    "stock", new BigDecimal("0.00")
            );
            scenario = "QDII(新基金)";
        } else {
            // 权益类：以天天基金为主，降低股票权重
            weights = Map.of(
                    "eastmoney", new BigDecimal("0.70"),
                    "sina", new BigDecimal("0.30"),
                    "stock", new BigDecimal("0.00")
            );
            scenario = "权益类(新基金)";
        }

        log.info("新基金保守权重: type={}, stockSource={}, 场景={}", fundType, stockSourceType, scenario);
        return new WeightResult(weights, scenario, false, true);
    }

    /**
     * 检查基金是否有足够的历史数据用于准确度修正
     *
     * @param fundCode 基金代码
     * @return 是否有至少3天的历史数据
     */
    public boolean hasEnoughHistoryData(String fundCode) {
        long count = estimatePredictionMapper.selectCount(
                new QueryWrapper<EstimatePrediction>()
                        .eq("fund_code", fundCode)
                        .isNotNull("actual_return")
        );
        return count >= 3;
    }

    /**
     * 应用历史准确度修正
     *
     * @param fundCode       基金代码
     * @param baseWeights    基础权重
     * @param availableSources 可用数据源
     * @return 修正后的权重
     */
    public WeightResult applyAccuracyCorrection(String fundCode,
                                                 Map<String, BigDecimal> baseWeights,
                                                 Set<String> availableSources,
                                                 boolean isColdStart) {
        try {
            Map<String, BigDecimal> multipliers = calculateAccuracyMultipliers(fundCode, availableSources);
            if (multipliers == null || multipliers.size() < 2) {
                return new WeightResult(baseWeights, null, false, isColdStart);
            }

            Map<String, BigDecimal> finalWeights = new LinkedHashMap<>();
            for (String key : availableSources) {
                BigDecimal bw = baseWeights.getOrDefault(key, new BigDecimal("0.25"));
                BigDecimal multiplier = multipliers.getOrDefault(key, BigDecimal.ONE);
                finalWeights.put(key, bw.multiply(multiplier));
            }

            log.info("基金{}应用准确度修正, 修正因子: {}", fundCode, multipliers);
            return new WeightResult(finalWeights, null, true, isColdStart);
        } catch (Exception e) {
            log.warn("准确度修正计算失败: {}", fundCode, e);
            return new WeightResult(baseWeights, null, false, isColdStart);
        }
    }

    /**
     * 计算准确度修正乘数
     * 基于最近3个交易日的MAE（平均绝对误差）
     */
    private Map<String, BigDecimal> calculateAccuracyMultipliers(String fundCode, Set<String> sourceKeys) {
        List<EstimatePrediction> predictions = estimatePredictionMapper.selectList(
                new QueryWrapper<EstimatePrediction>()
                        .eq("fund_code", fundCode)
                        .isNotNull("actual_return")
                        .in("source_key", sourceKeys)
                        .orderByDesc("predict_date")
        );

        if (predictions.isEmpty()) {
            return null;
        }

        // 按 source_key 分组
        Map<String, List<EstimatePrediction>> bySource = new LinkedHashMap<>();
        for (EstimatePrediction p : predictions) {
            bySource.computeIfAbsent(p.getSourceKey(), k -> new ArrayList<>()).add(p);
        }

        // 计算各源MAE（至少3条记录）
        Map<String, BigDecimal> maeMap = new LinkedHashMap<>();
        int minRecords = 3;

        for (Map.Entry<String, List<EstimatePrediction>> entry : bySource.entrySet()) {
            List<EstimatePrediction> records = entry.getValue();
            List<EstimatePrediction> recent = records.size() > minRecords
                    ? records.subList(0, minRecords)
                    : records;
            if (recent.size() < minRecords) {
                continue;
            }

            BigDecimal sumError = BigDecimal.ZERO;
            for (EstimatePrediction p : recent) {
                BigDecimal error = p.getReturnError() != null
                        ? p.getReturnError().abs()
                        : BigDecimal.ZERO;
                sumError = sumError.add(error);
            }
            BigDecimal mae = sumError.divide(new BigDecimal(recent.size()), 4, RoundingMode.HALF_UP);
            maeMap.put(entry.getKey(), mae);
        }

        if (maeMap.size() < 2) {
            return null;
        }

        // 转换为修正乘数: multiplier = 1 / (1 + MAE)
        Map<String, BigDecimal> multipliers = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : maeMap.entrySet()) {
            BigDecimal multiplier = BigDecimal.ONE.divide(
                    BigDecimal.ONE.add(entry.getValue()), 4, RoundingMode.HALF_UP);
            multipliers.put(entry.getKey(), multiplier);
        }

        log.info("基金{}准确度修正乘数: {}", fundCode, multipliers);
        return multipliers;
    }

    /**
     * 生成权重描述文案
     */
    public String buildWeightDescription(String fundType,
                                          String stockSourceType,
                                          BigDecimal coverageRatio,
                                          boolean accuracyEnhanced) {
        StringBuilder desc = new StringBuilder();

        if ("etf_realtime".equals(stockSourceType)) {
            desc.append("基于ETF实时价格的高置信度加权");
        } else if ("MONEY".equals(fundType)) {
            desc.append("货币基金估值参考意义有限（日涨幅约0.01%，波动极小）");
        } else if ("BOND".equals(fundType)) {
            desc.append("债券基金估值波动较小，机构估值相对可靠");
        } else if ("QDII".equals(fundType)) {
            desc.append("QDII基金加权平均（预估今日海外市场涨跌，T+1晚公布实际值）");
        } else {
            BigDecimal cov = coverageRatio != null ? coverageRatio : BigDecimal.ZERO;
            String covStr = cov.setScale(0, RoundingMode.HALF_UP).toPlainString();
            if (cov.compareTo(new BigDecimal("60")) >= 0) {
                desc.append("多源加权平均（重仓股覆盖率").append(covStr).append("%，权重35%）");
            } else if (cov.compareTo(new BigDecimal("30")) >= 0) {
                desc.append("多源加权平均（重仓股覆盖率").append(covStr).append("%，权重降至15%）");
            } else {
                desc.append("多源加权平均（重仓股覆盖率仅").append(covStr).append("%，权重降至5%）");
            }
        }

        if (accuracyEnhanced) {
            desc.append(" + 历史准确度修正");
        }

        return desc.toString();
    }

    /**
     * 归一化权重
     */
    public Map<String, BigDecimal> normalizeWeights(Map<String, BigDecimal> weights) {
        BigDecimal totalWeight = weights.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalWeight.compareTo(BigDecimal.ZERO) == 0) {
            return weights;
        }

        Map<String, BigDecimal> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, BigDecimal> entry : weights.entrySet()) {
            normalized.put(entry.getKey(),
                    entry.getValue().divide(totalWeight, 4, RoundingMode.HALF_UP));
        }
        return normalized;
    }
}
