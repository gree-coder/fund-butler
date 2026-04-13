package com.qoder.fund.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 天天基金数据源
 * 获取基金业绩、评级、风险指标等数据
 */
@Slf4j
@Component
public class TiantianFundDataSource {

    private static final String SOURCE_NAME = "tiantian";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    // 天天基金API基础URL
    private static final String FUND_DETAIL_API = "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNFInfo";
    private static final String FUND_PERFORMANCE_API = "https://fundmobapi.eastmoney.com/FundMNewApi/FundMNHisNetList";

    public TiantianFundDataSource(OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取基金详情（包含业绩、评级等）
     */
    public Map<String, Object> getFundDetail(String fundCode) {
        try {
            String url = String.format(
                    "%s?plat=Android&appType=ttjj&product=EFund&Version=6.3.8&FCode=%s",
                    FUND_DETAIL_API, fundCode);

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G960U)")
                    .header("Referer", "https://fund.eastmoney.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    log.warn("天天基金API请求失败: HTTP {}", response.code());
                    return Collections.emptyMap();
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);

                if (root.has("Datas")) {
                    return parseFundDetail(root.get("Datas"));
                }
            }
        } catch (Exception e) {
            log.error("获取天天基金详情失败: code={}", fundCode, e);
        }

        return Collections.emptyMap();
    }

    /**
     * 解析基金详情
     */
    private Map<String, Object> parseFundDetail(JsonNode data) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 基础信息
            result.put("fundCode", getTextValue(data, "FCODE"));
            result.put("fundName", getTextValue(data, "SHORTNAME"));
            result.put("fundType", getTextValue(data, "FTYPE"));
            result.put("manager", getTextValue(data, "JJJL"));
            result.put("company", getTextValue(data, "JJGS"));

            // 净值信息
            result.put("latestNav", parseBigDecimal(getTextValue(data, "NAV")));
            result.put("accumulatedNav", parseBigDecimal(getTextValue(data, "ACCNAV")));
            result.put("navDate", getTextValue(data, "PDATE"));

            // 业绩数据（多周期）
            Map<String, BigDecimal> performance = new HashMap<>();
            performance.put("1week", parseBigDecimal(getTextValue(data, "RZDF")));
            performance.put("1month", parseBigDecimal(getTextValue(data, "SYL_Y")));
            performance.put("3month", parseBigDecimal(getTextValue(data, "SYL_3Y")));
            performance.put("6month", parseBigDecimal(getTextValue(data, "SYL_6Y")));
            performance.put("1year", parseBigDecimal(getTextValue(data, "SYL_1N")));
            performance.put("2year", parseBigDecimal(getTextValue(data, "SYL_2N")));
            performance.put("3year", parseBigDecimal(getTextValue(data, "SYL_3N")));
            performance.put("5year", parseBigDecimal(getTextValue(data, "SYL_5N")));
            performance.put("sinceLaunch", parseBigDecimal(getTextValue(data, "SYL_LN")));
            result.put("performance", performance);

            // 评级数据
            result.put("morningStarRating", getIntValue(data, "MStar"));
            result.put("shanghaiSecRating", getIntValue(data, "RLEVEL_SZ"));
            result.put("shenzhenSecRating", getIntValue(data, "RLEVEL_SC"));

            // 风险指标
            result.put("riskLevel", getIntValue(data, "RISKLEVEL"));
            result.put("sharpeRatio", parseBigDecimal(getTextValue(data, "SHARP")));
            result.put("maxDrawdown", parseBigDecimal(getTextValue(data, "MAXDRAWDOWN")));

            // 规模信息
            result.put("fundSize", parseBigDecimal(getTextValue(data, "ENDNAV")));
            result.put("establishDate", getTextValue(data, "ESTABDATE"));

            // 费率信息
            result.put("managementFee", parseBigDecimal(getTextValue(data, "MGREED")));
            result.put("custodyFee", parseBigDecimal(getTextValue(data, "CUSTREED")));

        } catch (Exception e) {
            log.error("解析基金详情失败", e);
        }

        return result;
    }

    /**
     * 获取基金历史净值（用于计算额外指标）
     */
    public Map<String, Object> getFundHistory(String fundCode, int pageSize) {
        try {
            String url = String.format(
                    "%s?plat=Android&appType=ttjj&product=EFund&Version=6.3.8&FCode=%s&pageIndex=1&pageSize=%d",
                    FUND_PERFORMANCE_API, fundCode, pageSize);

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; SM-G960U)")
                    .header("Referer", "https://fund.eastmoney.com/")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    return Collections.emptyMap();
                }

                String body = response.body().string();
                JsonNode root = objectMapper.readTree(body);

                Map<String, Object> result = new HashMap<>();
                result.put("history", root.get("Datas"));
                return result;
            }
        } catch (Exception e) {
            log.error("获取基金历史净值失败: code={}", fundCode, e);
        }

        return Collections.emptyMap();
    }

    private String getTextValue(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return "";
    }

    private int getIntValue(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asInt();
        }
        return 0;
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
