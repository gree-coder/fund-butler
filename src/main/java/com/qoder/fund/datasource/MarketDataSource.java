package com.qoder.fund.datasource;

import com.qoder.fund.dto.MarketOverviewDTO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * 市场数据源
 * 获取大盘指数、板块热度等宏观数据
 */
@Slf4j
@Component
public class MarketDataSource {

    private final OkHttpClient httpClient;

    // 新浪大盘指数API
    private static final String SINA_INDEX_API = "https://hq.sinajs.cn/list=%s";

    // 新浪K线历史API
    private static final String SINA_KLINE_API = "https://money.finance.sina.com.cn/quotes_service/api/json_v2.php/CN_MarketData.getKLineData";

    // 大盘指数代码映射
    private static final String[][] INDEX_CODES = {
            {"s_sh000001", "上证指数"},
            {"s_sz399001", "深证成指"},
            {"s_sz399006", "创业板指"},
            {"s_sh000300", "沪深300"}
    };

    public MarketDataSource(OkHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * 获取大盘指数数据
     */
    public List<MarketOverviewDTO.IndexData> getMarketIndices() {
        List<MarketOverviewDTO.IndexData> indices = new ArrayList<>();

        StringBuilder codeList = new StringBuilder();
        for (int i = 0; i < INDEX_CODES.length; i++) {
            if (i > 0) {
                codeList.append(",");
            }
            codeList.append(INDEX_CODES[i][0]);
        }

        try {
            String url = String.format(SINA_INDEX_API, codeList.toString());
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Referer", "https://finance.sina.com.cn/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("获取大盘指数数据失败: HTTP {}", response.code());
                    return indices;
                }

                // 新浪返回GBK编码
                String body = new String(response.body().bytes(), Charset.forName("GBK"));
                indices = parseIndexData(body);
            }
        } catch (Exception e) {
            log.error("获取大盘指数数据异常", e);
        }

        return indices;
    }

    /**
     * 解析指数数据
     * 格式: var hq_str_s_sh000001="上证指数,3268.11,2.54,0.08,2345678,12345678";
     */
    private List<MarketOverviewDTO.IndexData> parseIndexData(String body) {
        List<MarketOverviewDTO.IndexData> indices = new ArrayList<>();

        for (String[] indexInfo : INDEX_CODES) {
            String code = indexInfo[0];
            String name = indexInfo[1];

            try {
                String varName = "hq_str_" + code;
                int startIdx = body.indexOf(varName);
                if (startIdx < 0) {
                    continue;
                }

                int quoteStart = body.indexOf('"', startIdx);
                int quoteEnd = body.indexOf('"', quoteStart + 1);
                if (quoteStart < 0 || quoteEnd <= quoteStart) {
                    continue;
                }

                String content = body.substring(quoteStart + 1, quoteEnd);
                String[] parts = content.split(",");

                // 格式: 名称(0),当前点数(1),涨跌点数(2),涨跌%(3),成交量(4),成交额(5)
                if (parts.length >= 6) {
                    MarketOverviewDTO.IndexData index = new MarketOverviewDTO.IndexData();
                    index.setCode(code.replace("s_", ""));
                    index.setName(name);
                    index.setCurrentPoint(parseBigDecimal(parts[1]));
                    index.setChangePoint(parseBigDecimal(parts[2]));
                    index.setChangePercent(parseBigDecimal(parts[3]));
                    index.setVolume(parseLong(parts[4]));
                    index.setTurnover(parseLong(parts[5]));

                    // 判断趋势
                    if (index.getChangePercent().compareTo(BigDecimal.ZERO) > 0) {
                        index.setTrend("up");
                    } else if (index.getChangePercent().compareTo(BigDecimal.ZERO) < 0) {
                        index.setTrend("down");
                    } else {
                        index.setTrend("flat");
                    }

                    indices.add(index);
                }
            } catch (Exception e) {
                log.warn("解析指数数据失败: code={}", code, e);
            }
        }

        return indices;
    }

    // K线API使用的指数代码（不带s_前缀）
    private static final String[][] KLINE_INDEX_CODES = {
            {"sh000001", "上证指数"},
            {"sz399001", "深证成指"},
            {"sz399006", "创业板指"},
            {"sh000300", "沪深300"}
    };

    /**
     * 获取大盘指数近期K线走势
     * @param days 天数（交易日）
     */
    public List<MarketOverviewDTO.IndexTrend> getIndexTrends(int days) {
        List<MarketOverviewDTO.IndexTrend> trends = new ArrayList<>();

        for (String[] indexInfo : KLINE_INDEX_CODES) {
            String code = indexInfo[0];
            String name = indexInfo[1];

            try {
                String url = String.format(
                        "%s?symbol=%s&scale=240&ma=no&datalen=%d",
                        SINA_KLINE_API, code, days);

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        .header("Referer", "https://finance.sina.com.cn/")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful() || response.body() == null) {
                        log.warn("获取指数K线数据失败: code={}, HTTP {}", code, response.code());
                        continue;
                    }

                    String body = response.body().string();
                    MarketOverviewDTO.IndexTrend trend = parseKLineData(code, name, body);
                    if (trend != null) {
                        trends.add(trend);
                    }
                }
            } catch (Exception e) {
                log.warn("获取指数K线数据异常: code={}", code, e);
            }
        }

        return trends;
    }

    /**
     * 解析K线数据
     * 新浪返回格式: [{"day":"2026-04-07","open":"3884.151","high":"3902.607","low":"3875.684","close":"3890.165","volume":"47361507600"}, ...]
     */
    private MarketOverviewDTO.IndexTrend parseKLineData(String code, String name, String body) {
        try {
            if (body == null || body.trim().isEmpty() || body.trim().equals("null")) {
                return null;
            }

            // 简单JSON数组解析（避免引入额外依赖）
            // 格式: [{"day":"...","open":"...","high":"...","low":"...","close":"...","volume":"..."},...]
            body = body.trim();
            if (!body.startsWith("[")) {
                return null;
            }

            MarketOverviewDTO.IndexTrend trend = new MarketOverviewDTO.IndexTrend();
            trend.setCode(code);
            trend.setName(name);
            List<MarketOverviewDTO.DailyKLine> dailyData = new ArrayList<>();

            // 按 },{ 分割每条记录
            String content = body.substring(1, body.length() - 1); // 去掉外层[]
            String[] items = content.split("\\},\\s*\\{");

            for (String item : items) {
                item = item.replace("{", "").replace("}", "").trim();
                MarketOverviewDTO.DailyKLine kline = new MarketOverviewDTO.DailyKLine();

                // 提取各字段值
                kline.setDate(extractJsonValue(item, "day"));
                kline.setOpen(parseBigDecimal(extractJsonValue(item, "open")));
                kline.setClose(parseBigDecimal(extractJsonValue(item, "close")));
                kline.setHigh(parseBigDecimal(extractJsonValue(item, "high")));
                kline.setLow(parseBigDecimal(extractJsonValue(item, "low")));
                kline.setVolume(parseLong(extractJsonValue(item, "volume")));

                dailyData.add(kline);
            }

            // 计算每日涨跌幅（基于前一日收盘价）
            for (int i = 1; i < dailyData.size(); i++) {
                BigDecimal prevClose = dailyData.get(i - 1).getClose();
                BigDecimal curClose = dailyData.get(i).getClose();
                if (prevClose != null && prevClose.compareTo(BigDecimal.ZERO) > 0 && curClose != null) {
                    BigDecimal changePercent = curClose.subtract(prevClose)
                            .multiply(new BigDecimal("100"))
                            .divide(prevClose, 2, RoundingMode.HALF_UP);
                    dailyData.get(i).setChangePercent(changePercent);
                }
            }

            trend.setDailyData(dailyData);

            // 计算区间涨跌幅（首日开盘到末日收盘）
            if (dailyData.size() >= 2) {
                BigDecimal firstClose = dailyData.get(0).getClose();
                BigDecimal lastClose = dailyData.get(dailyData.size() - 1).getClose();
                if (firstClose != null && firstClose.compareTo(BigDecimal.ZERO) > 0 && lastClose != null) {
                    trend.setPeriodChangePercent(lastClose.subtract(firstClose)
                            .multiply(new BigDecimal("100"))
                            .divide(firstClose, 2, RoundingMode.HALF_UP));
                }
            }

            return trend;

        } catch (Exception e) {
            log.warn("解析K线数据失败: code={}", code, e);
            return null;
        }
    }

    /**
     * 从简单JSON字符串中提取字段值
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int startIdx = json.indexOf(searchKey);
        if (startIdx < 0) {
            return null;
        }
        startIdx += searchKey.length();
        int endIdx = json.indexOf('"', startIdx);
        if (endIdx < 0) {
            return null;
        }
        return json.substring(startIdx, endIdx);
    }

    /**
     * 获取板块数据（模拟数据，实际可接入东方财富板块API）
     */
    public List<MarketOverviewDTO.SectorData> getLeadingSectors() {
        // TODO: 接入真实的板块数据API
        // 目前返回模拟数据，展示结构
        List<MarketOverviewDTO.SectorData> sectors = new ArrayList<>();

        // 模拟领涨板块
        sectors.add(createSector("半导体", "5.23", "中芯国际", "up"));
        sectors.add(createSector("新能源", "3.87", "宁德时代", "up"));
        sectors.add(createSector("医药生物", "2.95", "药明康德", "up"));

        return sectors;
    }

    /**
     * 获取领跌板块
     */
    public List<MarketOverviewDTO.SectorData> getDecliningSectors() {
        List<MarketOverviewDTO.SectorData> sectors = new ArrayList<>();

        // 模拟领跌板块
        sectors.add(createSector("银行", "-1.56", "招商银行", "down"));
        sectors.add(createSector("房地产", "-2.34", "万科A", "down"));
        sectors.add(createSector("煤炭", "-1.89", "中国神华", "down"));

        return sectors;
    }

    private MarketOverviewDTO.SectorData createSector(String name, String changePercent,
                                                       String leadingStock, String trend) {
        MarketOverviewDTO.SectorData sector = new MarketOverviewDTO.SectorData();
        sector.setName(name);
        sector.setChangePercent(new BigDecimal(changePercent));
        sector.setLeadingStock(leadingStock);
        sector.setTrend(trend);
        return sector;
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            if (value == null || value.trim().isEmpty() || value.equals("--")) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(value.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Long parseLong(String value) {
        try {
            if (value == null || value.trim().isEmpty()) {
                return 0L;
            }
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
