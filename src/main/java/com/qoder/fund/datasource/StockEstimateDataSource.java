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
     * 估算涨幅 = Σ(重仓股i的实时涨幅 × 持仓比例i) / 总持仓比例
     */
    public Map<String, Object> estimateByStocks(String fundCode, BigDecimal lastNav) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 1. 获取基金的重仓股数据
            Fund fund = fundMapper.selectById(fundCode);
            if (fund == null || fund.getTopHoldings() == null || fund.getTopHoldings().isEmpty()) {
                log.warn("基金重仓股数据不存在: {}", fundCode);
                return result;
            }

            List<Map<String, Object>> holdings = fund.getTopHoldings();

            // 2. 提取股票代码
            List<String> stockCodes = new ArrayList<>();
            Map<String, BigDecimal> ratioMap = new HashMap<>();
            for (Map<String, Object> h : holdings) {
                String stockCode = String.valueOf(h.get("stockCode"));
                BigDecimal ratio = toBigDecimal(h.get("ratio"));
                if (stockCode != null && !stockCode.isEmpty() && ratio.compareTo(BigDecimal.ZERO) > 0) {
                    String formattedCode = formatStockCode(stockCode);
                    stockCodes.add(formattedCode);
                    ratioMap.put(formattedCode, ratio);
                }
            }

            if (stockCodes.isEmpty()) return result;

            // 3. 获取股票实时行情
            Map<String, BigDecimal> stockReturns = fetchStockReturns(stockCodes);

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
                log.info("股票兜底估值: fund={}, return={}, coverage={}%", fundCode, estimateReturn, totalRatio);
            }
        } catch (Exception e) {
            log.error("股票估值兜底失败: fund={}", fundCode, e);
        }
        return result;
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
                            BigDecimal change = parseBigDecimal(diff.path("f3").asText(""));
                            if (!code.isEmpty()) {
                                // 查找匹配的完整代码
                                for (String fullCode : stockCodes) {
                                    if (fullCode.contains(code)) {
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
     * 格式化股票代码为东财格式: 1.600519 (沪) / 0.000001 (深)
     */
    private String formatStockCode(String code) {
        if (code == null) return "";
        code = code.trim();
        if (code.contains(".")) {
            // 已经有市场前缀
            if (code.startsWith("6")) return "1." + code;
            if (code.startsWith("0") || code.startsWith("3")) return "0." + code;
            return code;
        }
        if (code.startsWith("6")) return "1." + code;
        if (code.startsWith("0") || code.startsWith("3")) return "0." + code;
        return "1." + code;
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
