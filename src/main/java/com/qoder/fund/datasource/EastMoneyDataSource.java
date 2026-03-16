package com.qoder.fund.datasource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qoder.fund.dto.FundDetailDTO;
import com.qoder.fund.dto.FundSearchDTO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东方财富/天天基金 数据源适配器
 */
@Slf4j
@Component
public class EastMoneyDataSource implements FundDataSource {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public EastMoneyDataSource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String getName() {
        return "EastMoney";
    }

    @Override
    public List<FundSearchDTO> searchFund(String keyword) {
        try {
            // 天天基金搜索接口
            String url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx"
                    + "?callback=&m=1&key=" + keyword;
            String body = httpGet(url);
            if (body == null || body.isEmpty()) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(body);
            JsonNode datas = root.path("Datas");
            List<FundSearchDTO> results = new ArrayList<>();

            if (datas.isArray()) {
                for (JsonNode item : datas) {
                    String code = item.path("CODE").asText("");
                    String name = item.path("NAME").asText("");
                    String type = item.path("FundBaseInfo").path("FTYPE").asText("");
                    if (code.isEmpty()) continue;
                    FundSearchDTO dto = new FundSearchDTO();
                    dto.setCode(code);
                    dto.setName(cleanHtml(name));
                    dto.setType(mapFundType(type));
                    results.add(dto);
                }
            }
            return results;
        } catch (Exception e) {
            log.error("搜索基金失败: keyword={}", keyword, e);
            return Collections.emptyList();
        }
    }

    @Override
    public FundDetailDTO getFundDetail(String fundCode) {
        try {
            FundDetailDTO dto = new FundDetailDTO();
            dto.setCode(fundCode);

            // 1. 基本信息
            fetchBasicInfo(fundCode, dto);

            // 2. 最新净值和估值
            fetchLatestNav(fundCode, dto);

            // 3. 历史业绩
            fetchPerformance(fundCode, dto);

            // 4. 持仓数据
            fetchHoldings(fundCode, dto);

            return dto;
        } catch (Exception e) {
            log.error("获取基金详情失败: code={}", fundCode, e);
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getNavHistory(String fundCode, String startDate, String endDate) {
        // 1. 先尝试lsjz API
        List<Map<String, Object>> results = fetchNavFromLsjz(fundCode, startDate, endDate);
        if (!results.isEmpty()) return results;

        // 2. 降级到pingzhongdata的Data_netWorthTrend
        return fetchNavFromPingzhong(fundCode, startDate, endDate);
    }

    private List<Map<String, Object>> fetchNavFromLsjz(String fundCode, String startDate, String endDate) {
        try {
            String url = "https://api.fund.eastmoney.com/f10/lsjz?fundCode=" + fundCode
                    + "&pageIndex=1&pageSize=365&startDate=" + startDate + "&endDate=" + endDate;
            String body = httpGetWithReferer(url);
            if (body == null) return Collections.emptyList();

            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("Data");
            if (data.isNull() || data.isMissingNode()) return Collections.emptyList();
            JsonNode list = data.path("LSJZList");
            List<Map<String, Object>> results = new ArrayList<>();

            if (list.isArray()) {
                for (JsonNode item : list) {
                    Map<String, Object> nav = new HashMap<>();
                    nav.put("navDate", item.path("FSRQ").asText(""));
                    nav.put("nav", parseBigDecimal(item.path("DWJZ").asText("")));
                    nav.put("accNav", parseBigDecimal(item.path("LJJZ").asText("")));
                    nav.put("dailyReturn", parseBigDecimal(item.path("JZZZL").asText("")));
                    results.add(nav);
                }
            }
            Collections.reverse(results);
            return results;
        } catch (Exception e) {
            log.warn("lsjz API获取净值历史失败: code={}", fundCode, e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> fetchNavFromPingzhong(String fundCode, String startDate, String endDate) {
        try {
            String url = "https://fund.eastmoney.com/pingzhongdata/" + fundCode + ".js";
            String body = httpGet(url);
            if (body == null) return Collections.emptyList();

            String navTrend = extractJsArray(body, "Data_netWorthTrend");
            if (navTrend == null) return Collections.emptyList();

            JsonNode arr = objectMapper.readTree(navTrend);
            if (!arr.isArray()) return Collections.emptyList();

            java.time.LocalDate start = (startDate != null && !startDate.isEmpty())
                    ? java.time.LocalDate.parse(startDate) : java.time.LocalDate.of(2000, 1, 1);
            java.time.LocalDate end = (endDate != null && !endDate.isEmpty())
                    ? java.time.LocalDate.parse(endDate) : java.time.LocalDate.now();

            List<Map<String, Object>> results = new ArrayList<>();
            for (JsonNode item : arr) {
                long ts = item.path("x").asLong(0);
                if (ts <= 0) continue;
                java.time.LocalDate date = java.time.Instant.ofEpochMilli(ts)
                        .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate();
                if (date.isBefore(start) || date.isAfter(end)) continue;

                Map<String, Object> nav = new HashMap<>();
                nav.put("navDate", date.toString());
                nav.put("nav", parseBigDecimal(item.path("y").asText("")));
                nav.put("accNav", BigDecimal.ZERO);
                nav.put("dailyReturn", parseBigDecimal(item.path("equityReturn").asText("")));
                results.add(nav);
            }
            log.info("从pingzhongdata获取净值历史: code={}, count={}", fundCode, results.size());
            return results;
        } catch (Exception e) {
            log.warn("pingzhongdata获取净值历史失败: code={}", fundCode, e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getEstimateNav(String fundCode) {
        try {
            // 天天基金估值接口
            String url = "https://fundgz.1234567.com.cn/js/" + fundCode + ".js";
            String body = httpGet(url);
            if (body == null || body.isEmpty()) return Collections.emptyMap();

            // 解析 jsonpgz({...})
            int start = body.indexOf('{');
            int end = body.lastIndexOf('}');
            if (start < 0 || end < 0) return Collections.emptyMap();

            String json = body.substring(start, end + 1);
            JsonNode node = objectMapper.readTree(json);

            Map<String, Object> result = new HashMap<>();
            result.put("estimateNav", parseBigDecimal(node.path("gsz").asText("")));
            result.put("estimateReturn", parseBigDecimal(node.path("gszzl").asText("")));
            result.put("estimateTime", node.path("gztime").asText(""));
            result.put("fundCode", node.path("fundcode").asText(""));
            result.put("fundName", node.path("name").asText(""));
            return result;
        } catch (Exception e) {
            log.warn("获取估值失败(将使用兜底): code={}", fundCode, e);
            return Collections.emptyMap();
        }
    }

    // ====================== 私有方法 ======================

    private void fetchBasicInfo(String fundCode, FundDetailDTO dto) {
        try {
            String url = "https://fund.eastmoney.com/pingzhongdata/" + fundCode + ".js";
            String body = httpGet(url);
            if (body == null) return;

            // 解析JS变量
            dto.setName(extractJsVar(body, "fS_name"));
            dto.setCode(extractJsVar(body, "fS_code"));

            // 费率信息
            String sourceRate = extractJsVar(body, "fund_sourceRate");
            String currentRate = extractJsVar(body, "fund_Rate");
            if (sourceRate != null || currentRate != null) {
                Map<String, Object> feeRate = new HashMap<>();
                feeRate.put("purchaseRate", sourceRate != null ? sourceRate + "%" : null);
                feeRate.put("discountRate", currentRate != null ? currentRate + "%" : null);
                dto.setFeeRate(feeRate);
            }

            // 基金经理
            String managerStr = extractJsArray(body, "Data_currentFundManager");
            if (managerStr != null) {
                try {
                    JsonNode managers = objectMapper.readTree(managerStr);
                    if (managers.isArray() && !managers.isEmpty()) {
                        JsonNode mgr = managers.get(0);
                        dto.setManager(mgr.path("name").asText(""));
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.warn("获取基本信息失败: code={}", fundCode, e);
        }

        // 补充详细信息(估值接口)
        try {
            String url = "https://fundgz.1234567.com.cn/js/" + fundCode + ".js";
            String body = httpGet(url);
            if (body != null && body.contains("{")) {
                int start = body.indexOf('{');
                int end = body.lastIndexOf('}');
                if (start >= 0 && end >= 0) {
                    JsonNode node = objectMapper.readTree(body.substring(start, end + 1));
                    if (dto.getName() == null || dto.getName().isEmpty()) {
                        dto.setName(node.path("name").asText(""));
                    }
                }
            }
        } catch (Exception ignored) {}

        // 从F10页面获取基金类型、公司、成立日期、规模等详细信息
        fetchF10Info(fundCode, dto);
    }

    private void fetchF10Info(String fundCode, FundDetailDTO dto) {
        try {
            String url = "https://fundf10.eastmoney.com/jbgk_" + fundCode + ".html";
            String body = httpGet(url);
            if (body == null) return;

            // 解析基金类型
            String type = extractHtmlTableValue(body, "基金类型");
            if (type != null && !type.isEmpty()) {
                dto.setType(mapFundTypeFromDetail(type));
            }

            // 解析基金公司
            String company = extractHtmlTableValue(body, "基金管理人");
            if (company != null) {
                dto.setCompany(company.replaceAll("<[^>]+>", "").trim());
            }

            // 解析成立日期
            String establishStr = extractHtmlTableValue(body, "成立日期/规模");
            if (establishStr != null) {
                Matcher dateMatcher = Pattern.compile("(\\d{4})年(\\d{2})月(\\d{2})日").matcher(establishStr);
                if (dateMatcher.find()) {
                    dto.setEstablishDate(dateMatcher.group(1) + "-" + dateMatcher.group(2) + "-" + dateMatcher.group(3));
                }
            }

            // 解析净资产规模
            String scaleStr = extractHtmlTableValue(body, "净资产规模");
            if (scaleStr != null) {
                Matcher scaleMatcher = Pattern.compile("([\\d.]+)亿元").matcher(scaleStr);
                if (scaleMatcher.find()) {
                    dto.setScale(new BigDecimal(scaleMatcher.group(1)));
                }
            }

            // 解析管理费率
            String mgmtFeeStr = extractHtmlTableValue(body, "管理费率");
            if (mgmtFeeStr != null) {
                Matcher feeMatcher = Pattern.compile("([\\d.]+)%").matcher(mgmtFeeStr);
                if (feeMatcher.find()) {
                    Map<String, Object> feeRate = dto.getFeeRate() != null ? dto.getFeeRate() : new HashMap<>();
                    feeRate.put("managementFee", feeMatcher.group(1) + "%");
                    dto.setFeeRate(feeRate);
                }
            }

            // 解析托管费率
            String custodyFeeStr = extractHtmlTableValue(body, "托管费率");
            if (custodyFeeStr != null) {
                Matcher feeMatcher = Pattern.compile("([\\d.]+)%").matcher(custodyFeeStr);
                if (feeMatcher.find()) {
                    Map<String, Object> feeRate = dto.getFeeRate() != null ? dto.getFeeRate() : new HashMap<>();
                    feeRate.put("custodyFee", feeMatcher.group(1) + "%");
                    dto.setFeeRate(feeRate);
                }
            }
        } catch (Exception e) {
            log.warn("获取F10信息失败: code={}", fundCode, e);
        }
    }

    private String extractHtmlTableValue(String html, String label) {
        try {
            int idx = html.indexOf(">" + label + "</th>");
            if (idx < 0) idx = html.indexOf(">" + label + "</td>");
            if (idx < 0) return null;
            int tdStart = html.indexOf("<td", idx);
            if (tdStart < 0) return null;
            int contentStart = html.indexOf(">", tdStart) + 1;
            int tdEnd = html.indexOf("</td>", contentStart);
            if (tdEnd < 0) return null;
            return html.substring(contentStart, tdEnd).trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String mapFundTypeFromDetail(String type) {
        if (type == null) return null;
        String clean = type.replaceAll("<[^>]+>", "").trim();
        if (clean.contains("股票")) return "STOCK";
        if (clean.contains("混合")) return "MIXED";
        if (clean.contains("债券")) return "BOND";
        if (clean.contains("货币")) return "MONEY";
        if (clean.contains("QDII")) return "QDII";
        if (clean.contains("指数")) return "INDEX";
        return clean;
    }

    private void fetchLatestNav(String fundCode, FundDetailDTO dto) {
        try {
            // 从pingzhongdata提取最新净值(Data_netWorthTrend)
            String url = "https://fund.eastmoney.com/pingzhongdata/" + fundCode + ".js";
            String body = httpGet(url);
            if (body != null) {
                String navTrend = extractJsArray(body, "Data_netWorthTrend");
                if (navTrend != null) {
                    try {
                        JsonNode arr = objectMapper.readTree(navTrend);
                        if (arr.isArray() && !arr.isEmpty()) {
                            JsonNode latest = arr.get(arr.size() - 1);
                            BigDecimal nav = parseBigDecimal(latest.path("y").asText(""));
                            long timestamp = latest.path("x").asLong(0);
                            if (nav.compareTo(BigDecimal.ZERO) > 0) {
                                dto.setLatestNav(nav);
                                if (timestamp > 0) {
                                    dto.setLatestNavDate(java.time.Instant.ofEpochMilli(timestamp)
                                            .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                                            .toLocalDate().toString());
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("解析Data_netWorthTrend失败: code={}", fundCode, e);
                    }
                }
            }

            // 估值
            Map<String, Object> estimate = getEstimateNav(fundCode);
            if (!estimate.isEmpty()) {
                dto.setEstimateNav((BigDecimal) estimate.get("estimateNav"));
                dto.setEstimateReturn((BigDecimal) estimate.get("estimateReturn"));
            }
        } catch (Exception e) {
            log.warn("获取净值失败: code={}", fundCode, e);
        }
    }

    private void fetchPerformance(String fundCode, FundDetailDTO dto) {
        try {
            // 从净值历史计算业绩
            String url = "https://fund.eastmoney.com/pingzhongdata/" + fundCode + ".js";
            String body = httpGet(url);
            if (body == null) return;

            String growthStr = extractJsArray(body, "syl_1n");
            String growthStr3m = extractJsArray(body, "syl_3y");
            String growthStr6m = extractJsArray(body, "syl_6y");

            FundDetailDTO.PerformanceDTO perf = new FundDetailDTO.PerformanceDTO();
            // 从净值数据计算简单业绩(近1月)
            perf.setMonth1(parseBigDecimalOrNull(extractJsSimpleVar(body, "syl_1y")));
            perf.setMonth3(parseBigDecimalOrNull(extractJsSimpleVar(body, "syl_3y")));
            perf.setMonth6(parseBigDecimalOrNull(extractJsSimpleVar(body, "syl_6y")));
            perf.setYear1(parseBigDecimalOrNull(extractJsSimpleVar(body, "syl_1n")));
            dto.setPerformance(perf);
        } catch (Exception e) {
            log.warn("获取业绩失败: code={}", fundCode, e);
        }
    }

    private void fetchHoldings(String fundCode, FundDetailDTO dto) {
        try {
            // 从F10持仓明细接口获取十大重仓股(含股票名称和占比)
            String url = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=" + fundCode + "&topline=10";
            String body = httpGetWithReferer(url);
            if (body != null && body.contains("<tbody>")) {
                List<Map<String, Object>> holdings = new ArrayList<>();
                // 解析表格行
                Pattern rowPattern = Pattern.compile("<tr>\\s*<td>(\\d+)</td>.*?</tr>", Pattern.DOTALL);
                Matcher rowMatcher = rowPattern.matcher(body);
                while (rowMatcher.find() && holdings.size() < 10) {
                    String row = rowMatcher.group();
                    Pattern tdPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
                    Matcher tdMatcher = tdPattern.matcher(row);
                    List<String> cells = new ArrayList<>();
                    while (tdMatcher.find()) {
                        cells.add(tdMatcher.group(1).replaceAll("<[^>]+>", "").trim());
                    }
                    // cells: [序号, 股票代码, 股票名称, 最新价, 涨跌幅, 相关资讯, 占净值比例, 持股数, 持仓市值]
                    if (cells.size() >= 7) {
                        Map<String, Object> h = new HashMap<>();
                        h.put("stockCode", cells.get(1));
                        h.put("stockName", cells.get(2));
                        h.put("ratio", parseBigDecimal(cells.get(6)));
                        holdings.add(h);
                    }
                }
                if (!holdings.isEmpty()) {
                    dto.setTopHoldings(holdings);
                }
            }
        } catch (Exception e) {
            log.warn("获取持仓失败: code={}", fundCode, e);
        }
    }

    private String httpGet(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (Exception e) {
            log.warn("HTTP GET 失败: url={}", url, e);
        }
        return null;
    }

    private String httpGetWithReferer(String url) {
        try {
            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://fundf10.eastmoney.com/")
                    .header("Accept", "*/*")
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return response.body().string();
                }
            }
        } catch (Exception e) {
            log.warn("HTTP GET 失败: url={}", url, e);
        }
        return null;
    }

    private String extractJsVar(String js, String varName) {
        Pattern pattern = Pattern.compile("var\\s+" + varName + "\\s*=\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(js);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractJsSimpleVar(String js, String varName) {
        Pattern pattern = Pattern.compile("var\\s+" + varName + "\\s*=\\s*([^;]+);");
        Matcher matcher = pattern.matcher(js);
        if (matcher.find()) {
            String val = matcher.group(1).trim().replace("\"", "").replace("'", "");
            if (val.isEmpty() || val.equals("null") || val.equals("undefined") || val.equals("--")) return null;
            return val;
        }
        return null;
    }

    private String extractJsArray(String js, String varName) {
        Pattern pattern = Pattern.compile("var\\s+" + varName + "\\s*=\\s*(\\[.*?\\]|\"[^\"]*\"|'[^']*')\\s*;", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(js);
        return matcher.find() ? matcher.group(1) : null;
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            if (value == null || value.isEmpty() || value.equals("--") || value.equals("null")) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(value.replace("%", "").trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseBigDecimalOrNull(String value) {
        try {
            if (value == null || value.isEmpty() || value.equals("--") || value.equals("null")) {
                return null;
            }
            return new BigDecimal(value.replace("%", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private String cleanHtml(String text) {
        return text == null ? "" : text.replaceAll("<[^>]+>", "");
    }

    private String mapFundType(String type) {
        if (type == null) return "MIXED";
        return switch (type) {
            case "股票型", "股票指数" -> "STOCK";
            case "混合型" -> "MIXED";
            case "债券型", "债券指数" -> "BOND";
            case "货币型" -> "MONEY";
            case "QDII", "QDII-指数" -> "QDII";
            case "指数型" -> "INDEX";
            default -> "MIXED";
        };
    }
}
