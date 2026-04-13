package com.qoder.fund.service;

import com.qoder.fund.dto.DashboardDTO;
import com.qoder.fund.dto.MarketOverviewDTO;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.datasource.MarketDataSource;
import com.qoder.fund.datasource.SectorDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 市场概览服务
 * 整合大盘指数、板块热度、持仓影响分析
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MarketOverviewService {

    private final MarketDataSource marketDataSource;
    private final SectorDataSource sectorDataSource;
    private final DashboardService dashboardService;

    /**
     * 获取市场概览
     * 缓存5分钟（大盘数据变化较快）
     */
    @Cacheable(value = "marketOverview", key = "'current'", unless = "#result == null", cacheManager = "marketCacheManager")
    public MarketOverviewDTO getMarketOverview() {
        log.info("开始获取市场概览");
        long startTime = System.currentTimeMillis();

        try {
            MarketOverviewDTO overview = new MarketOverviewDTO();
            overview.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

            // 1. 获取大盘指数
            List<MarketOverviewDTO.IndexData> indices = marketDataSource.getMarketIndices();
            overview.setIndices(indices);

            // 2. 获取板块数据（使用新的板块数据源）
            overview.setLeadingSectors(sectorDataSource.getLeadingSectors());
            overview.setDecliningSectors(sectorDataSource.getDecliningSectors());

            // 3. 获取大盘指数近期走势（近5个交易日）
            List<MarketOverviewDTO.IndexTrend> indexTrends = marketDataSource.getIndexTrends(5);
            overview.setIndexTrends(indexTrends);

            // 4. 分析市场情绪
            String sentiment = analyzeMarketSentiment(indices);
            overview.setMarketSentiment(sentiment);
            overview.setSentimentDescription(generateSentimentDescription(sentiment, indices));

            // 5. 分析对持仓的影响
            overview.setPortfolioImpact(analyzePortfolioImpact(indices));

            log.info("市场概览获取完成，指数数量: {}, 走势数据: {}条, 耗时: {}ms",
                    indices.size(), indexTrends != null ? indexTrends.size() : 0,
                    System.currentTimeMillis() - startTime);

            return overview;

        } catch (Exception e) {
            log.error("获取市场概览失败", e);
            return createEmptyOverview();
        }
    }

    /**
     * 分析市场情绪
     */
    private String analyzeMarketSentiment(List<MarketOverviewDTO.IndexData> indices) {
        if (indices == null || indices.isEmpty()) {
            return "neutral";
        }

        // 统计涨跌
        int upCount = 0;
        int downCount = 0;
        BigDecimal totalChange = BigDecimal.ZERO;

        for (MarketOverviewDTO.IndexData index : indices) {
            if (index.getChangePercent() != null) {
                if (index.getChangePercent().compareTo(BigDecimal.ZERO) > 0) {
                    upCount++;
                } else if (index.getChangePercent().compareTo(BigDecimal.ZERO) < 0) {
                    downCount++;
                }
                totalChange = totalChange.add(index.getChangePercent());
            }
        }

        BigDecimal avgChange = totalChange.divide(new BigDecimal(indices.size()), 2, RoundingMode.HALF_UP);

        // 判断情绪：优先看平均涨跌幅，其次看涨跌数量
        if (avgChange.compareTo(new BigDecimal("1")) > 0) {
            return "bullish";
        } else if (avgChange.compareTo(new BigDecimal("-1")) < 0) {
            return "bearish";
        } else if (upCount > downCount + 1) {
            return "bullish";
        } else if (downCount > upCount + 1) {
            return "bearish";
        } else {
            return "neutral";
        }
    }

    /**
     * 生成情绪描述
     */
    private String generateSentimentDescription(String sentiment, List<MarketOverviewDTO.IndexData> indices) {
        StringBuilder desc = new StringBuilder();

        switch (sentiment) {
            case "bullish":
                desc.append("今日市场整体表现积极，");
                break;
            case "bearish":
                desc.append("今日市场整体表现偏弱，");
                break;
            default:
                desc.append("今日市场整体表现平稳，");
        }

        // 添加主要指数表现
        if (indices != null && !indices.isEmpty()) {
            MarketOverviewDTO.IndexData shIndex = indices.stream()
                    .filter(i -> "sh000001".equals(i.getCode()))
                    .findFirst()
                    .orElse(indices.get(0));

            if (shIndex.getChangePercent().compareTo(BigDecimal.ZERO) > 0) {
                desc.append(String.format("%s上涨%.2f%%",
                        shIndex.getName(), shIndex.getChangePercent()));
            } else if (shIndex.getChangePercent().compareTo(BigDecimal.ZERO) < 0) {
                desc.append(String.format("%s下跌%.2f%%",
                        shIndex.getName(), shIndex.getChangePercent().abs()));
            } else {
                desc.append(String.format("%s平盘", shIndex.getName()));
            }
        }

        return desc.toString();
    }

    /**
     * 分析对持仓的影响
     */
    private MarketOverviewDTO.PortfolioImpact analyzePortfolioImpact(List<MarketOverviewDTO.IndexData> indices) {
        MarketOverviewDTO.PortfolioImpact impact = new MarketOverviewDTO.PortfolioImpact();

        try {
            // 获取持仓数据
            DashboardDTO dashboard = dashboardService.getDashboard();
            List<PositionDTO> positions = dashboard.getPositions();

            if (positions == null || positions.isEmpty()) {
                impact.setOverallImpact("neutral");
                impact.setDescription("暂无持仓数据");
                impact.setRelatedFundCount(0);
                impact.setSuggestion("添加持仓后可查看市场影响分析");
                return impact;
            }

            // 基于大盘走势判断对持仓的影响
            BigDecimal avgIndexChange = indices.stream()
                    .map(MarketOverviewDTO.IndexData::getChangePercent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(new BigDecimal(indices.size()), 2, RoundingMode.HALF_UP);

            // 统计今日持仓预估表现
            BigDecimal totalEstimateReturn = BigDecimal.ZERO;
            int positiveCount = 0;
            int negativeCount = 0;

            for (PositionDTO position : positions) {
                if (position.getEstimateReturn() != null) {
                    totalEstimateReturn = totalEstimateReturn.add(position.getEstimateReturn());
                    if (position.getEstimateReturn().compareTo(BigDecimal.ZERO) > 0) {
                        positiveCount++;
                    } else if (position.getEstimateReturn().compareTo(BigDecimal.ZERO) < 0) {
                        negativeCount++;
                    }
                }
            }

            BigDecimal avgPositionReturn = positions.isEmpty() ? BigDecimal.ZERO :
                    totalEstimateReturn.divide(new BigDecimal(positions.size()), 2, RoundingMode.HALF_UP);

            // 判断影响
            if (avgPositionReturn.compareTo(new BigDecimal("1")) > 0) {
                impact.setOverallImpact("positive");
                impact.setDescription(String.format("您的持仓今日整体表现良好，平均上涨%.2f%%，跑赢大盘",
                        avgPositionReturn));
                impact.setSuggestion("市场氛围积极，可关注止盈机会");
            } else if (avgPositionReturn.compareTo(new BigDecimal("-1")) < 0) {
                impact.setOverallImpact("negative");
                impact.setDescription(String.format("您的持仓今日整体回调%.2f%%",
                        avgPositionReturn.abs()));
                impact.setSuggestion("市场偏弱，建议保持观望或逢低布局");
            } else {
                impact.setOverallImpact("neutral");
                impact.setDescription("您的持仓今日表现平稳");
                impact.setSuggestion("市场震荡，建议保持当前配置");
            }

            impact.setRelatedFundCount(positions.size());

        } catch (Exception e) {
            log.error("分析持仓影响失败", e);
            impact.setOverallImpact("neutral");
            impact.setDescription("无法分析持仓影响");
            impact.setRelatedFundCount(0);
            impact.setSuggestion("请稍后重试");
        }

        return impact;
    }

    /**
     * 创建空概览
     */
    private MarketOverviewDTO createEmptyOverview() {
        MarketOverviewDTO overview = new MarketOverviewDTO();
        overview.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        overview.setMarketSentiment("neutral");
        overview.setSentimentDescription("暂无市场数据");
        return overview;
    }
}
