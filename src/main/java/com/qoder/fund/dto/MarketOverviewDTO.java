package com.qoder.fund.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 市场概览 DTO
 * 大盘指数 + 板块热度 + 市场情绪
 */
@Data
public class MarketOverviewDTO {

    /**
     * 数据更新时间
     */
    private String updateTime;

    /**
     * 整体市场情绪: bullish(积极)/neutral(中性)/bearish(谨慎)
     */
    private String marketSentiment;

    /**
     * 市场情绪描述
     */
    private String sentimentDescription;

    /**
     * 大盘指数列表
     */
    private List<IndexData> indices;

    /**
     * 领涨板块
     */
    private List<SectorData> leadingSectors;

    /**
     * 领跌板块
     */
    private List<SectorData> decliningSectors;

    /**
     * 一级行业板块分类聚合 [{category, avgChangePercent, sectorCount, sectors}]
     */
    private List<Map<String, Object>> sectorCategories;

    /**
     * 大盘指数近期走势（近N个交易日）
     */
    private List<IndexTrend> indexTrends;

    /**
     * 对持仓的影响分析
     */
    private PortfolioImpact portfolioImpact;

    /**
     * 大盘指数数据
     */
    @Data
    public static class IndexData {
        /**
         * 指数代码
         */
        private String code;

        /**
         * 指数名称
         */
        private String name;

        /**
         * 当前点数
         */
        private BigDecimal currentPoint;

        /**
         * 涨跌点数
         */
        private BigDecimal changePoint;

        /**
         * 涨跌幅(%)
         */
        private BigDecimal changePercent;

        /**
         * 成交量(万手)
         */
        private Long volume;

        /**
         * 成交额(万元)
         */
        private Long turnover;

        /**
         * 趋势: up/down/flat
         */
        private String trend;
    }

    /**
     * 板块数据
     */
    @Data
    public static class SectorData {
        /**
         * 板块名称
         */
        private String name;

        /**
         * 涨跌幅(%)
         */
        private BigDecimal changePercent;

        /**
         * 领涨股
         */
        private String leadingStock;

        /**
         * 趋势: up/down
         */
        private String trend;

        /**
         * 近5日涨跌幅(%)
         */
        private BigDecimal change5d;

        /**
         * 近10日涨跌幅(%)
         */
        private BigDecimal change10d;
    }

    /**
     * 大盘指数近期走势
     */
    @Data
    public static class IndexTrend {
        /**
         * 指数代码
         */
        private String code;

        /**
         * 指数名称
         */
        private String name;

        /**
         * 每日K线数据
         */
        private List<DailyKLine> dailyData;

        /**
         * 区间涨跌幅(%)
         */
        private BigDecimal periodChangePercent;
    }

    /**
     * 日K线数据
     */
    @Data
    public static class DailyKLine {
        /**
         * 交易日期
         */
        private String date;

        /**
         * 开盘价
         */
        private BigDecimal open;

        /**
         * 收盘价
         */
        private BigDecimal close;

        /**
         * 最高价
         */
        private BigDecimal high;

        /**
         * 最低价
         */
        private BigDecimal low;

        /**
         * 成交量
         */
        private Long volume;

        /**
         * 当日涨跌幅(%)
         */
        private BigDecimal changePercent;
    }

    /**
     * 对持仓的影响
     */
    @Data
    public static class PortfolioImpact {
        /**
         * 整体影响: positive(正面)/neutral(中性)/negative(负面)
         */
        private String overallImpact;

        /**
         * 影响描述
         */
        private String description;

        /**
         * 相关持仓基金数量
         */
        private Integer relatedFundCount;

        /**
         * 建议操作
         */
        private String suggestion;
    }
}
