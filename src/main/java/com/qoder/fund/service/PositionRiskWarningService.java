package com.qoder.fund.service;

import com.qoder.fund.dto.DashboardDTO;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.dto.PositionRiskWarningDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 持仓风险预警服务
 * 基于规则引擎检测持仓风险
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PositionRiskWarningService {

    private final DashboardService dashboardService;

    // 风险阈值配置
    private static final BigDecimal SINGLE_POSITION_WARNING_THRESHOLD = new BigDecimal("30"); // 单一持仓警告阈值 30%
    private static final BigDecimal SINGLE_POSITION_CRITICAL_THRESHOLD = new BigDecimal("50"); // 单一持仓危险阈值 50%
    private static final BigDecimal INDUSTRY_WARNING_THRESHOLD = new BigDecimal("40"); // 单一行业警告阈值 40%
    private static final BigDecimal INDUSTRY_CRITICAL_THRESHOLD = new BigDecimal("60"); // 单一行业危险阈值 60%
    private static final int MIN_POSITIONS_FOR_DIVERSIFICATION = 3; // 最低分散持仓数量
    private static final BigDecimal HIGH_VALUATION_THRESHOLD = new BigDecimal("80"); // 高估值阈值 80%分位
    private static final BigDecimal PROFIT_NOTICE_THRESHOLD = new BigDecimal("-3"); // 亏损提示阈值 -3%
    private static final BigDecimal PROFIT_WARNING_THRESHOLD = new BigDecimal("-10"); // 亏损警告阈值 -10%
    private static final BigDecimal PROFIT_CRITICAL_THRESHOLD = new BigDecimal("-20"); // 亏损危险阈值 -20%
    private static final BigDecimal SINGLE_FUND_LOSS_THRESHOLD = new BigDecimal("-15"); // 单基金亏损阈值 -15%

    /**
     * 获取持仓风险预警报告
     * 结果缓存5分钟
     */
    @Cacheable(value = "positionRiskWarning", key = "'current'", unless = "#result == null", cacheManager = "analysisCacheManager")
    public PositionRiskWarningDTO getRiskWarning() {
        log.info("开始生成持仓风险预警报告");
        long startTime = System.currentTimeMillis();

        try {
            // 1. 获取 Dashboard 数据（包含持仓和行业分布）
            DashboardDTO dashboard = dashboardService.getDashboard();
            List<PositionDTO> positions = dashboard.getPositions();

            if (positions == null || positions.isEmpty()) {
                return createEmptyWarning();
            }

            // 2. 初始化结果
            PositionRiskWarningDTO warning = new PositionRiskWarningDTO();
            warning.setWarningTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            List<PositionRiskWarningDTO.RiskItem> risks = new ArrayList<>();

            // 3. 执行各项风险检测
            risks.addAll(checkConcentrationRisk(positions, dashboard.getTotalAsset()));
            risks.addAll(checkIndustryRisk(dashboard.getIndustryDistribution()));
            risks.addAll(checkDiversificationRisk(positions));
            risks.addAll(checkValuationRisk(positions));
            risks.addAll(checkProfitRisk(positions));
            risks.addAll(checkRiskLevelBalance(positions));

            warning.setRisks(risks);

            // 4. 计算健康指标
            warning.setHealthMetrics(calculateHealthMetrics(positions, dashboard, risks));

            // 5. 确定整体风险等级
            int riskScore = calculateRiskScore(risks);
            warning.setRiskScore(riskScore);
            warning.setOverallRiskLevel(determineOverallRiskLevel(riskScore, risks));

            // 6. 生成摘要
            warning.setSummary(generateSummary(warning.getOverallRiskLevel(), risks));

            // 7. 生成优化建议
            warning.setSuggestions(generateSuggestions(risks, positions));

            log.info("持仓风险预警报告生成完成，风险项: {}, 耗时: {}ms",
                    risks.size(), System.currentTimeMillis() - startTime);

            return warning;

        } catch (Exception e) {
            log.error("生成持仓风险预警报告失败", e);
            return createEmptyWarning();
        }
    }

    /**
     * 检测单一持仓集中度风险
     */
    private List<PositionRiskWarningDTO.RiskItem> checkConcentrationRisk(
            List<PositionDTO> positions, BigDecimal totalAsset) {
        List<PositionRiskWarningDTO.RiskItem> risks = new ArrayList<>();

        if (totalAsset == null || totalAsset.compareTo(BigDecimal.ZERO) <= 0) {
            return risks;
        }

        for (PositionDTO position : positions) {
            if (position.getMarketValue() == null) continue;

            BigDecimal ratio = position.getMarketValue()
                    .multiply(new BigDecimal("100"))
                    .divide(totalAsset, 2, RoundingMode.HALF_UP);

            if (ratio.compareTo(SINGLE_POSITION_CRITICAL_THRESHOLD) >= 0) {
                PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
                risk.setType("concentration");
                risk.setLevel("critical");
                risk.setTitle("单一持仓过度集中");
                risk.setDescription(String.format("%s(%s)占总投资比例过高，存在较大集中风险",
                        position.getFundName(), position.getFundCode()));
                risk.setRelatedItems(List.of(position.getFundName()));
                risk.setCurrentValue(ratio + "%");
                risk.setThreshold("< " + SINGLE_POSITION_CRITICAL_THRESHOLD + "%");
                risk.setSuggestion("建议适当减仓或分散投资，将单一持仓控制在30%以内");
                risks.add(risk);
            } else if (ratio.compareTo(SINGLE_POSITION_WARNING_THRESHOLD) >= 0) {
                PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
                risk.setType("concentration");
                risk.setLevel("high");
                risk.setTitle("单一持仓集中度偏高");
                risk.setDescription(String.format("%s(%s)占总投资比例为%.1f%%，建议关注",
                        position.getFundName(), position.getFundCode(), ratio));
                risk.setRelatedItems(List.of(position.getFundName()));
                risk.setCurrentValue(ratio + "%");
                risk.setThreshold("< " + SINGLE_POSITION_WARNING_THRESHOLD + "%");
                risk.setSuggestion("建议关注该持仓波动，考虑适当分散");
                risks.add(risk);
            }
        }

        return risks;
    }

    /**
     * 检测行业集中度风险
     */
    private List<PositionRiskWarningDTO.RiskItem> checkIndustryRisk(
            List<Map<String, Object>> industryDistribution) {
        List<PositionRiskWarningDTO.RiskItem> risks = new ArrayList<>();

        if (industryDistribution == null || industryDistribution.isEmpty()) {
            return risks;
        }

        for (Map<String, Object> industry : industryDistribution) {
            String industryName = (String) industry.get("industry");
            BigDecimal ratio = null;

            Object ratioObj = industry.get("ratio");
            if (ratioObj instanceof BigDecimal) {
                ratio = (BigDecimal) ratioObj;
            } else if (ratioObj instanceof Number) {
                ratio = new BigDecimal(ratioObj.toString());
            }

            if (ratio == null) continue;

            if (ratio.compareTo(INDUSTRY_CRITICAL_THRESHOLD) >= 0) {
                PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
                risk.setType("industry");
                risk.setLevel("critical");
                risk.setTitle("行业过度集中");
                risk.setDescription(String.format("%s行业占比过高，行业风险较大", industryName));
                risk.setRelatedItems(List.of(industryName));
                risk.setCurrentValue(ratio.setScale(1, RoundingMode.HALF_UP) + "%");
                risk.setThreshold("< " + INDUSTRY_CRITICAL_THRESHOLD + "%");
                risk.setSuggestion("建议增加其他行业配置，降低单一行业依赖");
                risks.add(risk);
            } else if (ratio.compareTo(INDUSTRY_WARNING_THRESHOLD) >= 0) {
                PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
                risk.setType("industry");
                risk.setLevel("high");
                risk.setTitle("行业集中度偏高");
                risk.setDescription(String.format("%s行业占比%.1f%%，建议适当分散",
                        industryName, ratio));
                risk.setRelatedItems(List.of(industryName));
                risk.setCurrentValue(ratio.setScale(1, RoundingMode.HALF_UP) + "%");
                risk.setThreshold("< " + INDUSTRY_WARNING_THRESHOLD + "%");
                risk.setSuggestion("可考虑增加其他行业基金配置");
                risks.add(risk);
            }
        }

        return risks;
    }

    /**
     * 检测持仓分散度风险
     */
    private List<PositionRiskWarningDTO.RiskItem> checkDiversificationRisk(List<PositionDTO> positions) {
        List<PositionRiskWarningDTO.RiskItem> risks = new ArrayList<>();

        int positionCount = positions.size();

        if (positionCount < MIN_POSITIONS_FOR_DIVERSIFICATION) {
            PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
            risk.setType("diversification");
            risk.setLevel(positionCount == 1 ? "high" : "medium");
            risk.setTitle("持仓过于集中");
            risk.setDescription(String.format("当前仅持有%d只基金，分散度不足", positionCount));
            risk.setRelatedItems(positions.stream().map(PositionDTO::getFundName).collect(Collectors.toList()));
            risk.setCurrentValue(positionCount + "只");
            risk.setThreshold(">= " + MIN_POSITIONS_FOR_DIVERSIFICATION + "只");
            risk.setSuggestion("建议至少持有3-5只不同风格的基金以分散风险");
            risks.add(risk);
        } else if (positionCount > 15) {
            PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
            risk.setType("diversification");
            risk.setLevel("low");
            risk.setTitle("持仓过于分散");
            risk.setDescription(String.format("当前持有%d只基金，过于分散可能导致管理困难", positionCount));
            risk.setRelatedItems(List.of("共" + positionCount + "只基金"));
            risk.setCurrentValue(positionCount + "只");
            risk.setThreshold("<= 15只");
            risk.setSuggestion("建议精简持仓，聚焦优质基金");
            risks.add(risk);
        }

        return risks;
    }

    /**
     * 检测估值风险
     */
    private List<PositionRiskWarningDTO.RiskItem> checkValuationRisk(List<PositionDTO> positions) {
        List<PositionRiskWarningDTO.RiskItem> risks = new ArrayList<>();

        // 这里简化处理，实际可以结合 AI 诊断的估值数据
        // 目前基于涨跌幅简单判断
        List<String> highValuationFunds = positions.stream()
                .filter(p -> p.getEstimateReturn() != null && p.getEstimateReturn().compareTo(new BigDecimal("5")) > 0)
                .map(PositionDTO::getFundName)
                .collect(Collectors.toList());

        if (highValuationFunds.size() >= 3) {
            PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
            risk.setType("valuation");
            risk.setLevel("medium");
            risk.setTitle("多只基金短期涨幅较大");
            risk.setDescription(String.format("有%d只基金今日涨幅超过5%%，需关注估值水平", highValuationFunds.size()));
            risk.setRelatedItems(highValuationFunds.subList(0, Math.min(3, highValuationFunds.size())));
            risk.setCurrentValue(highValuationFunds.size() + "只");
            risk.setThreshold("< 3只");
            risk.setSuggestion("建议关注短期涨幅过大的基金，避免追高");
            risks.add(risk);
        }

        return risks;
    }

    /**
     * 检测亏损风险
     */
    private List<PositionRiskWarningDTO.RiskItem> checkProfitRisk(List<PositionDTO> positions) {
        List<PositionRiskWarningDTO.RiskItem> risks = new ArrayList<>();

        List<PositionDTO> lossPositions = positions.stream()
                .filter(p -> p.getProfitRate() != null && p.getProfitRate().compareTo(BigDecimal.ZERO) < 0)
                .collect(Collectors.toList());

        BigDecimal totalCost = positions.stream()
                .map(PositionDTO::getCostAmount)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalProfit = positions.stream()
                .map(PositionDTO::getProfit)
                .filter(profit -> profit != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 检测整体亏损
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal overallProfitRate = totalProfit
                    .multiply(new BigDecimal("100"))
                    .divide(totalCost, 2, RoundingMode.HALF_UP);

            if (overallProfitRate.compareTo(PROFIT_CRITICAL_THRESHOLD) <= 0) {
                PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
                risk.setType("profit");
                risk.setLevel("critical");
                risk.setTitle("整体亏损严重");
                risk.setDescription(String.format("整体亏损%.1f%%，建议评估投资策略", overallProfitRate.abs()));
                risk.setRelatedItems(lossPositions.stream()
                        .map(PositionDTO::getFundName).collect(Collectors.toList()));
                risk.setCurrentValue(overallProfitRate + "%");
                risk.setThreshold("> " + PROFIT_WARNING_THRESHOLD + "%");
                risk.setSuggestion("建议审视持仓结构，考虑止损或定投摊薄成本");
                risks.add(risk);
            } else if (overallProfitRate.compareTo(PROFIT_WARNING_THRESHOLD) <= 0) {
                PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
                risk.setType("profit");
                risk.setLevel("high");
                risk.setTitle("整体亏损较大");
                risk.setDescription(String.format("整体亏损%.1f%%，需关注市场变化", overallProfitRate.abs()));
                risk.setRelatedItems(lossPositions.stream()
                        .map(PositionDTO::getFundName).limit(3).collect(Collectors.toList()));
                risk.setCurrentValue(overallProfitRate + "%");
                risk.setThreshold("> " + PROFIT_WARNING_THRESHOLD + "%");
                risk.setSuggestion("建议关注市场走势，适时调整持仓结构");
                risks.add(risk);
            } else if (overallProfitRate.compareTo(PROFIT_NOTICE_THRESHOLD) <= 0) {
                PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
                risk.setType("profit");
                risk.setLevel("medium");
                risk.setTitle("持仓出现亏损");
                risk.setDescription(String.format("整体亏损%.1f%%，%d只基金处于亏损状态",
                        overallProfitRate.abs(), lossPositions.size()));
                risk.setRelatedItems(lossPositions.stream()
                        .map(PositionDTO::getFundName).limit(3).collect(Collectors.toList()));
                risk.setCurrentValue(overallProfitRate + "%");
                risk.setThreshold("> 0%");
                risk.setSuggestion("建议关注亏损较大的基金，评估是否需要调整");
                risks.add(risk);
            }
        }

        // 检测单基金严重亏损
        List<PositionDTO> heavyLossFunds = lossPositions.stream()
                .filter(p -> p.getProfitRate().compareTo(SINGLE_FUND_LOSS_THRESHOLD) <= 0)
                .collect(Collectors.toList());

        if (!heavyLossFunds.isEmpty()) {
            PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
            risk.setType("profit");
            risk.setLevel(heavyLossFunds.size() >= 3 ? "high" : "medium");
            risk.setTitle("部分基金亏损较大");
            risk.setDescription(String.format("%d只基金亏损超过%.0f%%，需重点关注",
                    heavyLossFunds.size(), SINGLE_FUND_LOSS_THRESHOLD.abs()));
            risk.setRelatedItems(heavyLossFunds.stream()
                    .map(p -> String.format("%s(亏%.1f%%)", p.getFundName(), p.getProfitRate()))
                    .collect(Collectors.toList()));
            risk.setCurrentValue(heavyLossFunds.size() + "只");
            risk.setThreshold("亏损 < " + SINGLE_FUND_LOSS_THRESHOLD + "%");
            risk.setSuggestion("建议评估这些基金是否仍值得持有，考虑止损或定投摊低成本");
            risks.add(risk);
        }

        return risks;
    }

    /**
     * 检测风险等级平衡
     */
    private List<PositionRiskWarningDTO.RiskItem> checkRiskLevelBalance(List<PositionDTO> positions) {
        List<PositionRiskWarningDTO.RiskItem> risks = new ArrayList<>();

        // 基于基金类型判断风险等级
        Map<String, Long> fundTypeCount = positions.stream()
                .filter(p -> p.getFundType() != null)
                .collect(Collectors.groupingBy(PositionDTO::getFundType, Collectors.counting()));

        long highRiskCount = fundTypeCount.getOrDefault("STOCK", 0L)
                + fundTypeCount.getOrDefault("QDII", 0L);
        long lowRiskCount = fundTypeCount.getOrDefault("BOND", 0L)
                + fundTypeCount.getOrDefault("MONEY", 0L);

        // 完全没有低风险资产（债券/货币）
        if (lowRiskCount == 0 && positions.size() > 1) {
            PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
            risk.setType("risk_balance");
            risk.setLevel("high");
            risk.setTitle("缺乏低风险资产配置");
            risk.setDescription("持仓中没有债券型或货币型基金，全部为中高风险基金，回撤保护不足");
            risk.setRelatedItems(List.of("当前持仓 " + positions.size() + " 只均为权益类基金"));
            risk.setCurrentValue("0只低风险资产");
            risk.setThreshold(">= 1只债券/货币基金");
            risk.setSuggestion("建议配置10%-20%的债券型或货币型基金，在市场下跌时起到缓冲作用");
            risks.add(risk);
        } else if (highRiskCount == positions.size() && positions.size() > 1) {
            PositionRiskWarningDTO.RiskItem risk = new PositionRiskWarningDTO.RiskItem();
            risk.setType("risk_balance");
            risk.setLevel("medium");
            risk.setTitle("持仓风险等级偏高");
            risk.setDescription("所有持仓均为股票型或QDII基金，缺乏低风险配置");
            risk.setRelatedItems(List.of("高风险基金" + highRiskCount + "只"));
            risk.setCurrentValue("100%");
            risk.setThreshold("< 50%");
            risk.setSuggestion("建议配置部分债券型或货币型基金平衡风险");
            risks.add(risk);
        }

        return risks;
    }

    /**
     * 计算健康指标
     * 基于真实持仓数据计算各维度健康评分
     */
    private PositionRiskWarningDTO.HealthMetrics calculateHealthMetrics(
            List<PositionDTO> positions, DashboardDTO dashboard, List<PositionRiskWarningDTO.RiskItem> risks) {
        PositionRiskWarningDTO.HealthMetrics metrics = new PositionRiskWarningDTO.HealthMetrics();

        metrics.setTotalPositions(positions.size());

        // === 行业分散度评分：基于最高行业占比，而非行业数量 ===
        int industryScore = 90; // 默认较高
        if (dashboard.getIndustryDistribution() != null && !dashboard.getIndustryDistribution().isEmpty()) {
            BigDecimal maxIndustryRatio = dashboard.getIndustryDistribution().stream()
                    .map(ind -> {
                        Object r = ind.get("ratio");
                        if (r instanceof BigDecimal) return (BigDecimal) r;
                        if (r instanceof Number) return new BigDecimal(r.toString());
                        return BigDecimal.ZERO;
                    })
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            if (maxIndustryRatio.compareTo(new BigDecimal("60")) >= 0) {
                industryScore = 20;
            } else if (maxIndustryRatio.compareTo(new BigDecimal("40")) >= 0) {
                industryScore = 45;
            } else if (maxIndustryRatio.compareTo(new BigDecimal("25")) >= 0) {
                industryScore = 65;
            } else if (maxIndustryRatio.compareTo(new BigDecimal("15")) >= 0) {
                industryScore = 80;
            } else {
                industryScore = 90;
            }
        }
        metrics.setIndustryDiversification(industryScore);

        // === 集中度评分：基于最高单一持仓比例 ===
        int concentrationScore = 95;
        if (dashboard.getTotalAsset() != null && dashboard.getTotalAsset().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxPositionRatio = positions.stream()
                    .filter(p -> p.getMarketValue() != null)
                    .map(p -> p.getMarketValue()
                            .multiply(new BigDecimal("100"))
                            .divide(dashboard.getTotalAsset(), 2, RoundingMode.HALF_UP))
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);

            if (maxPositionRatio.compareTo(new BigDecimal("50")) >= 0) {
                concentrationScore = 15;
            } else if (maxPositionRatio.compareTo(new BigDecimal("30")) >= 0) {
                concentrationScore = 40;
            } else if (maxPositionRatio.compareTo(new BigDecimal("20")) >= 0) {
                concentrationScore = 65;
            } else if (maxPositionRatio.compareTo(new BigDecimal("15")) >= 0) {
                concentrationScore = 80;
            } else {
                concentrationScore = 95;
            }
        }
        metrics.setConcentrationScore(concentrationScore);

        // === 风险平衡评分：是否有低风险资产配置 ===
        Map<String, Long> fundTypeCount = positions.stream()
                .filter(p -> p.getFundType() != null)
                .collect(Collectors.groupingBy(PositionDTO::getFundType, Collectors.counting()));
        long lowRiskCount = fundTypeCount.getOrDefault("BOND", 0L)
                + fundTypeCount.getOrDefault("MONEY", 0L);
        int riskBalanceScore;
        if (lowRiskCount == 0) {
            riskBalanceScore = 30; // 完全没有低风险资产
        } else if ((double) lowRiskCount / positions.size() < 0.15) {
            riskBalanceScore = 60;
        } else {
            riskBalanceScore = 85;
        }
        metrics.setRiskBalanceScore(riskBalanceScore);

        // === 估值健康度：基于整体持仓盈亏 ===
        BigDecimal totalCost = positions.stream()
                .map(PositionDTO::getCostAmount)
                .filter(c -> c != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalProfit = positions.stream()
                .map(PositionDTO::getProfit)
                .filter(p -> p != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int valuationScore = 80;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal profitRate = totalProfit
                    .multiply(new BigDecimal("100"))
                    .divide(totalCost, 2, RoundingMode.HALF_UP);
            if (profitRate.compareTo(new BigDecimal("-30")) <= 0) {
                valuationScore = 20;
            } else if (profitRate.compareTo(new BigDecimal("-20")) <= 0) {
                valuationScore = 40;
            } else if (profitRate.compareTo(new BigDecimal("-10")) <= 0) {
                valuationScore = 60;
            } else if (profitRate.compareTo(new BigDecimal("-5")) <= 0) {
                valuationScore = 72;
            } else if (profitRate.compareTo(BigDecimal.ZERO) < 0) {
                valuationScore = 80;
            } else {
                valuationScore = 92;
            }
        }
        metrics.setValuationHealthScore(valuationScore);

        // === 整体健康度：四项加权平均 ===
        metrics.setOverallHealth(
                (industryScore + concentrationScore + riskBalanceScore + valuationScore) / 4);

        return metrics;
    }

    /**
     * 计算风险评分
     */
    private int calculateRiskScore(List<PositionRiskWarningDTO.RiskItem> risks) {
        int score = 0;
        for (PositionRiskWarningDTO.RiskItem risk : risks) {
            String level = risk.getLevel();
            if ("critical".equals(level)) {
                score += 30;
            } else if ("high".equals(level)) {
                score += 20;
            } else if ("medium".equals(level)) {
                score += 10;
            } else if ("low".equals(level)) {
                score += 5;
            }
        }
        return Math.min(100, score);
    }

    /**
     * 确定整体风险等级
     */
    private String determineOverallRiskLevel(int riskScore, List<PositionRiskWarningDTO.RiskItem> risks) {
        boolean hasCritical = risks.stream().anyMatch(r -> "critical".equals(r.getLevel()));
        boolean hasHigh = risks.stream().anyMatch(r -> "high".equals(r.getLevel()));

        if (hasCritical || riskScore >= 60) {
            return "high";
        } else if (hasHigh || riskScore >= 30) {
            return "medium";
        } else {
            return "low";
        }
    }

    /**
     * 生成摘要
     */
    private String generateSummary(String overallRiskLevel, List<PositionRiskWarningDTO.RiskItem> risks) {
        long criticalCount = risks.stream().filter(r -> "critical".equals(r.getLevel())).count();
        long highCount = risks.stream().filter(r -> "high".equals(r.getLevel())).count();
        long mediumCount = risks.stream().filter(r -> "medium".equals(r.getLevel())).count();

        StringBuilder summary = new StringBuilder();

        switch (overallRiskLevel) {
            case "high":
                summary.append("当前持仓存在较高风险，");
                break;
            case "medium":
                summary.append("当前持仓风险适中，");
                break;
            default:
                summary.append("当前持仓风险较低，");
        }

        if (criticalCount > 0) {
            summary.append(String.format("发现%d项严重风险需立即关注；", criticalCount));
        }
        if (highCount > 0) {
            summary.append(String.format("有%d项高风险", highCount));
            if (mediumCount > 0) {
                summary.append(String.format("和%d项中等风险", mediumCount));
            }
            summary.append("建议优化；");
        } else if (mediumCount > 0) {
            summary.append(String.format("有%d项中等风险建议关注；", mediumCount));
        }

        if (risks.isEmpty()) {
            summary.append("持仓结构良好，继续保持。");
        } else {
            summary.append("详情请查看下方风险项列表。");
        }

        return summary.toString();
    }

    /**
     * 生成优化建议
     */
    private List<PositionRiskWarningDTO.OptimizationSuggestion> generateSuggestions(
            List<PositionRiskWarningDTO.RiskItem> risks, List<PositionDTO> positions) {
        List<PositionRiskWarningDTO.OptimizationSuggestion> suggestions = new ArrayList<>();

        // 按风险等级排序
        List<PositionRiskWarningDTO.RiskItem> sortedRisks = risks.stream()
                .sorted((a, b) -> {
                    int priorityA = getLevelPriority(a.getLevel());
                    int priorityB = getLevelPriority(b.getLevel());
                    return Integer.compare(priorityB, priorityA);
                })
                .limit(3)
                .collect(Collectors.toList());

        for (PositionRiskWarningDTO.RiskItem risk : sortedRisks) {
            PositionRiskWarningDTO.OptimizationSuggestion suggestion = new PositionRiskWarningDTO.OptimizationSuggestion();
            suggestion.setType(risk.getType());
            suggestion.setPriority(risk.getLevel());
            suggestion.setTitle("优化" + risk.getTitle());
            suggestion.setContent(risk.getSuggestion());
            suggestion.setExpectedBenefit("降低" + getRiskTypeName(risk.getType()) + "风险");
            suggestions.add(suggestion);
        }

        // 如果没有风险，给出正面建议
        if (suggestions.isEmpty()) {
            PositionRiskWarningDTO.OptimizationSuggestion suggestion = new PositionRiskWarningDTO.OptimizationSuggestion();
            suggestion.setType("maintenance");
            suggestion.setPriority("low");
            suggestion.setTitle("保持当前配置");
            suggestion.setContent("当前持仓结构良好，建议继续保持，定期关注市场变化");
            suggestion.setExpectedBenefit("维持稳健收益");
            suggestions.add(suggestion);
        }

        return suggestions;
    }

    private int getLevelPriority(String level) {
        return switch (level) {
            case "critical" -> 4;
            case "high" -> 3;
            case "medium" -> 2;
            default -> 1;
        };
    }

    private String getRiskTypeName(String type) {
        return switch (type) {
            case "concentration" -> "集中";
            case "industry" -> "行业";
            case "diversification" -> "分散";
            case "valuation" -> "估值";
            case "profit" -> "亏损";
            case "risk_balance" -> "风险平衡";
            default -> "";
        };
    }

    /**
     * 获取风险类型优先级
     */
    private int getRiskTypePriority(String type) {
        return switch (type) {
            case "concentration" -> 1;
            case "industry" -> 2;
            case "diversification" -> 3;
            case "valuation" -> 4;
            case "profit" -> 5;
            case "risk_balance" -> 6;
            default -> 7;
        };
    }

    /**
     * 创建空预警（无持仓时）
     */
    private PositionRiskWarningDTO createEmptyWarning() {
        PositionRiskWarningDTO warning = new PositionRiskWarningDTO();
        warning.setWarningTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        warning.setOverallRiskLevel("low");
        warning.setRiskScore(0);
        warning.setSummary("暂无持仓数据，请添加持仓后查看风险预警");
        warning.setRisks(new ArrayList<>());

        PositionRiskWarningDTO.HealthMetrics metrics = new PositionRiskWarningDTO.HealthMetrics();
        metrics.setTotalPositions(0);
        metrics.setIndustryDiversification(0);
        metrics.setConcentrationScore(0);
        metrics.setRiskBalanceScore(0);
        metrics.setValuationHealthScore(0);
        metrics.setOverallHealth(0);
        warning.setHealthMetrics(metrics);

        warning.setSuggestions(new ArrayList<>());

        return warning;
    }
}
