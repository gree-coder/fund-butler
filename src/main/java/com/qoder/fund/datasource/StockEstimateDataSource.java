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
     * 沪A: 1.600519 (6开头)
     * 深A: 0.300170 (0/3开头, 6位数字)
     * 科创板: 1.688xxx
     * 港股/其他: 返回空字符串 (不参与计算)
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

        // 港股: 5位数字且以0开头 (如 00700, 09988, 01179)
        if (code.length() == 5 && code.startsWith("0")) {
            return "";
        }

        // 标准6位A股代码
        if (code.length() == 6) {
            if (code.startsWith("6")) return "1." + code;  // 沪市
            if (code.startsWith("0") || code.startsWith("3")) return "0." + code;  // 深市
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
