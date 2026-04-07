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

/**
 * 腾讯财经基金估值数据源
 * API: https://qt.gtimg.cn/q=jj{fundCode}
 * 响应格式: v_jj{code}="代码~名称~估值净值~估值涨幅~...~最新净值~累计净值~...~净值日期~";
 */
@Slf4j
@Component
public class TencentDataSource {

    private static final String SOURCE_NAME = "tencent";

    private final OkHttpClient httpClient;
    private final com.qoder.fund.config.CircuitBreaker circuitBreaker;

    // 腾讯API频率限制严格，使用同步锁强制串行请求
    private final Object requestLock = new Object();

    public TencentDataSource(OkHttpClient httpClient, com.qoder.fund.config.CircuitBreaker circuitBreaker) {
        this.httpClient = httpClient;
        this.circuitBreaker = circuitBreaker;
    }

    /**
     * 获取腾讯财经的基金实时估值
     * 注意：腾讯API有严格频率限制，使用同步锁强制串行请求
     */
    public Map<String, Object> getEstimateNav(String fundCode) {
        // 熔断检查
        if (!circuitBreaker.allowRequest(SOURCE_NAME)) {
            log.warn("腾讯数据源已熔断，跳过请求: {}", fundCode);
            return Collections.emptyMap();
        }

        // 同步锁强制串行，避免并发触发频率限制
        synchronized (requestLock) {
            // 再次检查熔断状态（可能在等待锁期间状态变化）
            if (!circuitBreaker.allowRequest(SOURCE_NAME)) {
                log.warn("腾讯数据源已熔断，跳过请求: {}", fundCode);
                return Collections.emptyMap();
            }
            return doGetEstimateNav(fundCode);
        }
    }

    private Map<String, Object> doGetEstimateNav(String fundCode) {
        try {
            String url = "https://qt.gtimg.cn/q=jj" + fundCode;

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .header("Referer", "https://gu.qq.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                String body = response.body().string();
                if (body == null || body.isEmpty()) {
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                // 格式: v_jj110011="code~name~estimateNav~estimateReturn~~latestNav~accNav~...~navDate~";
                int quoteStart = body.indexOf('"');
                int quoteEnd = body.lastIndexOf('"');
                if (quoteStart < 0 || quoteEnd <= quoteStart) {
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                String content = body.substring(quoteStart + 1, quoteEnd);
                String[] parts = content.split("~");

                // 至少需要9个字段
                if (parts.length < 9) {
                    log.warn("腾讯基金数据字段不足: code={}, fields={}", fundCode, parts.length);
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                // 字段2: 估值净值, 字段3: 估值涨幅
                BigDecimal estimateNav = parseBigDecimal(parts[2]);
                BigDecimal estimateReturn = parseBigDecimal(parts[3]);

                if (estimateNav.compareTo(BigDecimal.ZERO) <= 0) {
                    circuitBreaker.recordFailure(SOURCE_NAME);
                    return Collections.emptyMap();
                }

                // 记录成功
                circuitBreaker.recordSuccess(SOURCE_NAME);

                String navDate = parts.length > 8 ? parts[8].trim() : "";

                Map<String, Object> result = new HashMap<>();
                result.put("estimateNav", estimateNav);
                result.put("estimateReturn", estimateReturn);
                result.put("estimateTime", navDate);
                result.put("source", "tencent");
                log.debug("腾讯估值: fund={}, nav={}, return={}", fundCode, estimateNav, estimateReturn);
                return result;
            }
        } catch (Exception e) {
            log.warn("腾讯财经估值获取失败: code={}", fundCode, e);
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
