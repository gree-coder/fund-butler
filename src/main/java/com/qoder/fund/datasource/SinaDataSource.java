package com.qoder.fund.datasource;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 新浪财经基金估值数据源
 * API: https://hq.sinajs.cn/rn={timestamp}&list=fu_{fundCode}
 * 响应格式: var hq_str_fu_{code}="名称,估值时间,估值净值,最新净值,累计净值,净值变动,估值涨幅%,日期,...";
 */
@Slf4j
@Component
public class SinaDataSource {

    private static final String SOURCE_NAME = "sina";

    private final OkHttpClient httpClient;
    private final com.qoder.fund.config.CircuitBreaker circuitBreaker;

    public SinaDataSource(com.qoder.fund.config.CircuitBreaker circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 获取新浪财经的基金实时估值
     */
    public Map<String, Object> getEstimateNav(String fundCode) {
        // 熔断检查
        if (!circuitBreaker.allowRequest(SOURCE_NAME)) {
            log.warn("新浪数据源已熔断，跳过请求: {}", fundCode);
            return Collections.emptyMap();
        }

        try {
            String url = "https://hq.sinajs.cn/rn=" + System.currentTimeMillis()
                    + "&list=fu_" + fundCode;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Referer", "https://finance.sina.com.cn/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                String body = response.body().string();
                if (body == null || body.isEmpty() || body.contains("\"\"")) {
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                int quoteStart = body.indexOf('"');
                int quoteEnd = body.lastIndexOf('"');
                if (quoteStart < 0 || quoteEnd <= quoteStart) {
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                String content = body.substring(quoteStart + 1, quoteEnd);
                String[] parts = content.split(",");

                // fu_ 格式: 名称(0),估值时间(1),估值净值(2),最新净值(3),累计净值(4),净值变动(5),估值涨幅%(6),日期(7),...
                if (parts.length < 7) {
                    log.warn("新浪基金数据字段不足: code={}, fields={}", fundCode, parts.length);
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                BigDecimal estimateNav = parseBigDecimal(parts[2]);
                BigDecimal estimateReturn = parseBigDecimal(parts[6]);

                if (estimateNav.compareTo(BigDecimal.ZERO) <= 0) {
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                // 记录成功
                circuitBreaker.recordSuccess(SOURCE_NAME);

                Map<String, Object> result = new HashMap<>();
                result.put("estimateNav", estimateNav);
                result.put("estimateReturn", estimateReturn);
                result.put("estimateTime", parts[1].trim());
                result.put("source", "sina");
                log.debug("新浪估值: fund={}, nav={}, return={}", fundCode, estimateNav, estimateReturn);
                return result;
            }
        } catch (Exception e) {
            log.warn("新浪财经估值获取失败: code={}", fundCode, e);
            circuitBreaker.recordFailure(SOURCE_NAME);
            return Collections.emptyMap();
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            if (value == null || value.trim().isEmpty() || value.equals("--") || value.equals("null")) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(value.trim().replace("%", ""));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
