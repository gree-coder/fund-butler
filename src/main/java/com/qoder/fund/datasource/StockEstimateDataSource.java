package com.qoder.fund.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qoder.fund.entity.Fund;
import com.qoder.fund.mapper.FundMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 股票估值兜底数据源
 * 当外部估值接口不可用时，通过基金重仓股的实时行情加权计算基金估值
 */
@Slf4j
@Component
public class StockEstimateDataSource {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final FundMapper fundMapper;

    public StockEstimateDataSource(ObjectMapper objectMapper, FundMapper fundMapper) {
        this.objectMapper = objectMapper;
        this.fundMapper = fundMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 通过重仓股实时行情估算基金涨幅
     * ETF基金优先使用二级市场实时交易价格，非ETF基金使用重仓股加权计算
     * 优先使用完整持仓（年报/半年报），降级到十大重仓股
     */
    public Map<String, Object> estimateByStocks(String fundCode, BigDecimal lastNav) {
        // ETF基金直接使用二级市场实时价格
        if (isEtf(fundCode)) {
            Map<String, Object> etfResult = estimateByEtfPrice(fundCode, lastNav);
            if (!etfResult.isEmpty()) {
                return etfResult;
            }
            log.warn("ETF实时价格获取失败，降级到重仓股估算: {}", fundCode);
        }

        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 获取基金的持仓数据，优先使用完整持仓
            Fund fund = fundMapper.selectById(fundCode);
            if (fund == null) {
                log.warn("基金数据不存在: {}", fundCode);
                return result;
            }

            List<Map<String, Object>> holdings = null;
            String holdingsSource = "top10";

            // 优先使用完整持仓（年报/半年报）
            if (fund.getAllHoldings() != null && !fund.getAllHoldings().isEmpty()) {
                holdings = fund.getAllHoldings();
                holdingsSource = "all(" + holdings.size() + "只)";
            } else if (fund.getTopHoldings() != null && !fund.getTopHoldings().isEmpty()) {
                holdings = fund.getTopHoldings();
                holdingsSource = "top10";
            }

            if (holdings == null || holdings.isEmpty()) {
                log.warn("基金持仓数据不存在: {}", fundCode);
                return result;
            }

            // 2. 提取股票代码 (仅A股，跳过港股等非A股)
            List<String> stockCodes = new ArrayList<>();
            Map<String, BigDecimal> ratioMap = new HashMap<>();
            for (Map<String, Object> h : holdings) {
                String stockCode = String.valueOf(h.get("stockCode"));
                BigDecimal ratio = toBigDecimal(h.get("ratio"));
                if (stockCode == null || stockCode.isEmpty() || ratio.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                String formattedCode = formatStockCode(stockCode);
                if (formattedCode.isEmpty()) {
                    log.debug("跳过非A股: code={}, fund={}", stockCode, fundCode);
                    continue;
                }
                stockCodes.add(formattedCode);
                ratioMap.put(formattedCode, ratio);
            }

            if (stockCodes.isEmpty()) return result;

            // 3. 获取股票实时行情（分批请求，每批50只）
            Map<String, BigDecimal> stockReturns = fetchStockReturnsBatched(stockCodes);

            // 4. 加权计算估算涨幅
            BigDecimal totalRatio = BigDecimal.ZERO;
            BigDecimal weightedReturn = BigDecimal.ZERO;

            for (Map.Entry<String, BigDecimal> entry : ratioMap.entrySet()) {
                String code = entry.getKey();
                BigDecimal ratio = entry.getValue();
                BigDecimal stockReturn = stockReturns.getOrDefault(code, BigDecimal.ZERO);

                weightedReturn = weightedReturn.add(stockReturn.multiply(ratio));
                totalRatio = totalRatio.add(ratio);
            }

            if (totalRatio.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal estimateReturn = weightedReturn.divide(totalRatio, 4, RoundingMode.HALF_UP);
                BigDecimal estimateNav = lastNav.multiply(
                        BigDecimal.ONE.add(estimateReturn.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))
                ).setScale(4, RoundingMode.HALF_UP);

                result.put("estimateNav", estimateNav);
                result.put("estimateReturn", estimateReturn);
                result.put("source", "stock_estimate");
                result.put("coverageRatio", totalRatio);
                log.info("股票估值: fund={}, return={}, coverage={}%, holdings={}", fundCode, estimateReturn, totalRatio, holdingsSource);
            }
        } catch (Exception e) {
            log.error("股票估值失败: fund={}", fundCode, e);
        }
        return result;
    }

    /**
     * 通过ETF二级市场实时交易价格估算涨幅
     */
    private Map<String, Object> estimateByEtfPrice(String fundCode, BigDecimal lastNav) {
        Map<String, Object> result = new HashMap<>();
        try {
            String etfCode = formatEtfCode(fundCode);
            if (etfCode.isEmpty()) return result;

            String url = "https://push2.eastmoney.com/api/qt/ulist.np/get?"
                    + "fields=f12,f14,f3&secids=" + etfCode;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://quote.eastmoney.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonNode root = objectMapper.readTree(body);
                    JsonNode diffs = root.path("data").path("diff");

                    if (diffs.isArray() && diffs.size() > 0) {
                        JsonNode diff = diffs.get(0);
                        BigDecimal rawChange = parseBigDecimal(diff.path("f3").asText(""));
                        BigDecimal estimateReturn = rawChange.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

                        BigDecimal estimateNav = lastNav.multiply(
                                BigDecimal.ONE.add(estimateReturn.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))
                        ).setScale(4, RoundingMode.HALF_UP);

                        result.put("estimateNav", estimateNav);
                        result.put("estimateReturn", estimateReturn);
                        result.put("source", "etf_realtime");
                        log.info("ETF实时价格估值: fund={}, return={}%", fundCode, estimateReturn);
                    }
                }
            }
        } catch (Exception e) {
            log.error("ETF实时价格获取失败: fund={}", fundCode, e);
        }
        return result;
    }

    /**
     * 判断是否为ETF基金
     * 沪市ETF: 510xxx~518xxx (如 510050, 512100, 513050)
     * 深市ETF: 159xxx (如 159915, 159919)
     * 注意: 519xxx 是普通开放式基金，不是ETF
     */
    private boolean isEtf(String fundCode) {
        if (fundCode == null || fundCode.length() != 6) return false;
        if (fundCode.startsWith("159")) return true;
        if (fundCode.startsWith("51") && !fundCode.startsWith("519")) return true;
        return false;
    }

    /**
     * 格式化ETF代码为东财格式
     */
    private String formatEtfCode(String fundCode) {
        if (fundCode.startsWith("51") && !fundCode.startsWith("519")) return "1." + fundCode;   // 沪市ETF
        if (fundCode.startsWith("159")) return "0." + fundCode;  // 深市ETF
        return "";
    }

    /**
     * 批量获取股票实时涨跌幅（分批请求，每批最多50只股票）
     */
    private Map<String, BigDecimal> fetchStockReturnsBatched(List<String> stockCodes) {
        if (stockCodes.size() <= 50) {
            return fetchStockReturns(stockCodes);
        }

        Map<String, BigDecimal> allReturns = new HashMap<>();
        for (int i = 0; i < stockCodes.size(); i += 50) {
            int end = Math.min(i + 50, stockCodes.size());
            List<String> batch = stockCodes.subList(i, end);
            Map<String, BigDecimal> batchReturns = fetchStockReturns(batch);
            allReturns.putAll(batchReturns);
        }
        log.info("分批获取股票行情完成: 总数={}, 批次={}", stockCodes.size(), (stockCodes.size() + 49) / 50);
        return allReturns;
    }

    /**
     * 批量获取股票实时涨跌幅
     */
    private Map<String, BigDecimal> fetchStockReturns(List<String> stockCodes) {
        Map<String, BigDecimal> returns = new HashMap<>();
        try {
            String codes = String.join(",", stockCodes);
            String url = "https://push2.eastmoney.com/api/qt/ulist.np/get?"
                    + "fields=f12,f14,f3&secids=" + codes;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://quote.eastmoney.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonNode root = objectMapper.readTree(body);
                    JsonNode diffs = root.path("data").path("diff");

                    if (diffs.isArray()) {
                        for (JsonNode diff : diffs) {
                            String code = diff.path("f12").asText("");
                            // f3 的单位是百分之一 (如 -468 表示 -4.68%)，需要除以100
                            BigDecimal rawChange = parseBigDecimal(diff.path("f3").asText(""));
                            BigDecimal change = rawChange.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

                            if (!code.isEmpty()) {
                                // 精确匹配: fullCode 格式为 "1.600519"，code 为 "600519"
                                for (String fullCode : stockCodes) {
                                    String suffix = fullCode.contains(".") ? fullCode.substring(fullCode.indexOf('.') + 1) : fullCode;
                                    if (suffix.equals(code)) {
                                        returns.put(fullCode, change);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("获取股票行情失败", e);
        }
        return returns;
    }

    /**
     * 格式化股票代码为东财格式
     * 沪A: 1.600519 (6开头, 6位数字)
     * 深A: 0.300170 (0/3开头, 6位数字)
     * 科创板: 1.688xxx (6开头, 6位数字)
     * 港股: 116.00700 (4-5位数字)
     */
    private String formatStockCode(String code) {
        if (code == null) return "";
        code = code.trim();

        // 去掉可能的市场前缀 (如 "SH600519" -> "600519")
        if (code.startsWith("SH") || code.startsWith("SZ")) {
            code = code.substring(2);
        }

        // 必须是纯数字
        if (!code.matches("\\d+")) return "";

        // 标准6位A股代码
        if (code.length() == 6) {
            if (code.startsWith("6")) return "1." + code;  // 沪市
            if (code.startsWith("0") || code.startsWith("3")) return "0." + code;  // 深市
        }

        // 港股: 4-5位数字 (如 00700, 09988, 01179, 3690)
        if (code.length() == 4 || code.length() == 5) {
            return "116." + code;
        }

        return "";
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.toString().replace("%", "").trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            if (value == null || value.isEmpty() || value.equals("--")) return BigDecimal.ZERO;
            return new BigDecimal(value);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
