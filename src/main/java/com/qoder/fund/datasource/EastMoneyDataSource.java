package com.qoder.fund.datasource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qoder.fund.config.CircuitBreaker;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 东方财富/天天基金 数据源适配器
 */
@Slf4j
@Component
public class EastMoneyDataSource implements FundDataSource {

    private static final String SOURCE_NAME = "eastmoney";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;

    public EastMoneyDataSource(OkHttpClient httpClient, ObjectMapper objectMapper, CircuitBreaker circuitBreaker) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public String getName() {
        return "EastMoney";
    }

    @Override
    public List<FundSearchDTO> searchFund(String keyword) {
        // 熔断检查
        if (!circuitBreaker.allowRequest(SOURCE_NAME)) {
            log.warn("熔断器开启，跳过搜索请求: {}", keyword);
            return Collections.emptyList();
        }

        try {
            // 天天基金搜索接口
            String url = "https://fundsuggest.eastmoney.com/FundSearch/api/FundSearchAPI.ashx"
                    + "?callback=&m=1&key=" + keyword;
            String body = httpGet(url);
            if (body == null || body.isEmpty()) {
                circuitBreaker.recordFailure(SOURCE_NAME);
                return Collections.emptyList();
            }

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
        // 计算请求的日期范围
        java.time.LocalDate requestStart = java.time.LocalDate.parse(startDate);
        java.time.LocalDate requestEnd = java.time.LocalDate.parse(endDate);
        long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(requestStart, requestEnd);
        
        // 如果请求超过60天的数据，直接使用pingzhongdata（包含完整历史）
        // lsjz API 有分页限制，无法一次性获取大量历史数据
        if (daysBetween > 60) {
            return fetchNavFromPingzhong(fundCode, startDate, endDate);
        }
        
        // 1. 先尝试lsjz API（适合获取近期数据）
        List<Map<String, Object>> results = fetchNavFromLsjz(fundCode, startDate, endDate);
        if (!results.isEmpty()) {
            // 检查返回数据是否覆盖了请求的日期范围
            if (!results.isEmpty()) {
                java.time.LocalDate dataStart = java.time.LocalDate.parse((String) results.get(results.size() - 1).get("navDate"));
                // 如果数据起始日期晚于请求的起始日期超过7天，降级到pingzhongdata
                if (java.time.temporal.ChronoUnit.DAYS.between(requestStart, dataStart) > 7) {
                    return fetchNavFromPingzhong(fundCode, startDate, endDate);
                }
            }
            return results;
        }

        // 2. 降级到pingzhongdata的Data_netWorthTrend
        return fetchNavFromPingzhong(fundCode, startDate, endDate);
    }

    private List<Map<String, Object>> fetchNavFromLsjz(String fundCode, String startDate, String endDate) {
        try {
            String url = "https://api.fund.eastmoney.com/f10/lsjz?fundCode=" + fundCode
                    + "&pageIndex=1&pageSize=200&startDate=" + startDate + "&endDate=" + endDate;
            String body = httpGetWithReferer(url);
            if (body == null) {
                log.debug("lsjz API返回null: code={}", fundCode);
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(body);
            JsonNode data = root.path("Data");
            if (data.isNull() || data.isMissingNode()) {
                // 可能被限流: API返回了非预期结构
                String errMsg = root.path("ErrMsg").asText("");
                int errCode = root.path("ErrCode").asInt(-1);
                if (errCode != 0 || !errMsg.isEmpty()) {
                    log.warn("lsjz API返回错误: code={}, errCode={}, errMsg={}", fundCode, errCode, errMsg);
                } else {
                    log.debug("lsjz API Data为空: code={}, resp={}", fundCode,
                            body.substring(0, Math.min(200, body.length())));
                }
                return Collections.emptyList();
            }
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
            
            // 港股通基金：根据基金名称判断（如"沪港深"、"港股通"等）
            // 这些基金投资港股，收盘时间与A股不同（港股16:00收盘）
            if (!"QDII".equals(dto.getType()) && isHongKongFundByName(dto.getName())) {
                dto.setType("QDII");
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
        // QDII/海外 优先判断（"QDII-混合型"、"指数型-海外股票"应归为QDII）
        if (clean.contains("QDII") || clean.contains("海外")) return "QDII";
        // 港股通基金：含港股成分，收盘时间与A股不同
        // "港股"、"沪港深"等关键词表示基金投资港股市场
        if (clean.contains("港股") || clean.contains("沪港深") || clean.contains("深港")) return "QDII";
        // 债券优先于混合（"混合债券型"应归为BOND）
        if (clean.contains("债券")) return "BOND";
        if (clean.contains("货币")) return "MONEY";
        if (clean.contains("股票")) return "STOCK";
        if (clean.contains("混合")) return "MIXED";
        if (clean.contains("指数")) return "INDEX";
        return clean;
    }

    /**
     * 根据基金名称判断是否为港股相关基金
     * 用于在类型字段无法区分时，通过名称判断
     */
    private boolean isHongKongFundByName(String fundName) {
        if (fundName == null) return false;
        String name = fundName.toLowerCase();
        return name.contains("港股") || name.contains("沪港深") 
                || name.contains("深港通") || name.contains("港深");
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
                dto.setEstimateSource("天天基金实时估值");
            }
        } catch (Exception e) {
            log.warn("获取净值失败: code={}", fundCode, e);
        }
    }

    private void fetchPerformance(String fundCode, FundDetailDTO dto) {
        try {
            // 从pingzhongdata获取基础业绩
            String url = "https://fund.eastmoney.com/pingzhongdata/" + fundCode + ".js";
            String body = httpGet(url);

            FundDetailDTO.PerformanceDTO perf = new FundDetailDTO.PerformanceDTO();
            if (body != null) {
                perf.setMonth1(parseBigDecimalOrNull(extractJsSimpleVar(body, "syl_1y")));
                perf.setMonth3(parseBigDecimalOrNull(extractJsSimpleVar(body, "syl_3y")));
                perf.setMonth6(parseBigDecimalOrNull(extractJsSimpleVar(body, "syl_6y")));
                perf.setYear1(parseBigDecimalOrNull(extractJsSimpleVar(body, "syl_1n")));
            }

            // 从基金主页获取完整阶段涨幅（近1周、近3年、成立以来等）
            try {
                String mainUrl = "https://fund.eastmoney.com/" + fundCode + ".html";
                String mainBody = httpGet(mainUrl);
                if (mainBody != null) {
                    // 解析dd标签中的业绩摘要: <dd><span>近1周：</span><span class="...">-3.97%</span></dd>
                    Pattern ddPattern = Pattern.compile(
                            "<dd><span>([^<]+)</span>\\s*<span[^>]*>([^<]+)</span></dd>");
                    Matcher ddMatcher = ddPattern.matcher(mainBody);
                    while (ddMatcher.find()) {
                        String label = ddMatcher.group(1).trim();
                        String value = ddMatcher.group(2).trim();
                        if (label.contains("近3年")) {
                            perf.setYear3(parseBigDecimalOrNull(value));
                        } else if (label.contains("成立")) {
                            perf.setSinceEstablish(parseBigDecimalOrNull(value));
                        }
                    }

                    // 解析阶段涨幅表格获取近1周数据
                    // 表头: ['', '近1周', '近1月', '近3月', '近6月', '今年来', '近1年', '近2年', '近3年']
                    // 数据: ['阶段涨幅', '-3.97%', '-2.29%', ...]
                    int weekIdx = mainBody.indexOf("近1周");
                    if (weekIdx >= 0) {
                        int tableStart = mainBody.lastIndexOf("<table", weekIdx);
                        int tableEnd = mainBody.indexOf("</table>", weekIdx);
                        if (tableStart >= 0 && tableEnd >= 0) {
                            String table = mainBody.substring(tableStart, tableEnd);
                            // 找到"阶段涨幅"行，第2个td就是近1周
                            int rowIdx = table.indexOf("阶段涨幅");
                            if (rowIdx >= 0) {
                                String rowSection = table.substring(rowIdx);
                                Pattern tdPat = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
                                Matcher tdMat = tdPat.matcher(rowSection);
                                List<String> cells = new ArrayList<>();
                                while (tdMat.find()) {
                                    cells.add(tdMat.group(1).replaceAll("<[^>]+>", "").trim());
                                }
                                // substring从"阶段涨幅"开始，第1个<td>匹配的是近1周
                                if (cells.size() > 0) {
                                    perf.setWeek1(parseBigDecimalOrNull(cells.get(0)));
                                }
                                // 补充: 如果year3还没拿到，从表格取 (index 7)
                                if (perf.getYear3() == null && cells.size() > 7) {
                                    perf.setYear3(parseBigDecimalOrNull(cells.get(7)));
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("从基金主页获取完整业绩失败: code={}", fundCode, e);
            }

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

                // 解析持仓披露日期（格式：截止至：<font class='px12'>2025-12-31</font> 或 截止日期：2024-12-31）
                Pattern datePattern = Pattern.compile("截止(?:至|日期)[：:][^<]*<[^>]*>(\\d{4})-(\\d{2})-(\\d{2})");
                Matcher dateMatcher = datePattern.matcher(body);
                if (dateMatcher.find()) {
                    String year = dateMatcher.group(1);
                    String month = dateMatcher.group(2);
                    String day = dateMatcher.group(3);
                    dto.setHoldingsDate(String.format("%s-%s-%s", year, month, day));
                } else {
                    // 尝试匹配旧格式（报告期：2024年年报）
                    Pattern legacyDatePattern = Pattern.compile("(?:截止日期|报告期)[：:]\\s*(\\d{4})[-年](\\d{1,2})[-月]?(\\d{1,2})");
                    Matcher legacyMatcher = legacyDatePattern.matcher(body);
                    if (legacyMatcher.find()) {
                        dto.setHoldingsDate(String.format("%s-%02d-%02d",
                                legacyMatcher.group(1), Integer.parseInt(legacyMatcher.group(2)), Integer.parseInt(legacyMatcher.group(3))));
                    } else {
                        // 尝试匹配季度报告（如：2025年4季度股票投资明细）
                        Pattern quarterPattern = Pattern.compile("(\\d{4})年第?(\\d|一|二|三|四)季度");
                        Matcher quarterMatcher = quarterPattern.matcher(body);
                        if (quarterMatcher.find()) {
                            int year = Integer.parseInt(quarterMatcher.group(1));
                            String quarter = quarterMatcher.group(2);
                            int quarterNum = switch (quarter) {
                                case "一", "1" -> 1;
                                case "二", "2" -> 2;
                                case "三", "3" -> 3;
                                case "四", "4" -> 4;
                                default -> 1;
                            };
                            // Q1=3月底, Q2=6月底, Q3=9月底, Q4=12月底
                            int month = quarterNum * 3;
                            dto.setHoldingsDate(String.format("%d-%02d-30", year, month));
                        }
                    }
                }

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
                    // 丰富持仓数据：实时涨跌幅、行业信息、行业分布
                    enrichHoldingsWithRealtime(dto);
                }
            }
        } catch (Exception e) {
            log.warn("获取持仓失败: code={}", fundCode, e);
        }
    }

    /**
     * 通过东财股票实时行情API丰富持仓数据：
     * 1. 每只股票的实时涨跌幅和当前价格 (Task 7)
     * 2. 每只股票所属行业 → 聚合行业分布 (Task 3)
     * 3. 行业板块预估涨幅 (Task 5)
     */
    private void enrichHoldingsWithRealtime(FundDetailDTO dto) {
        List<Map<String, Object>> holdings = dto.getTopHoldings();
        if (holdings == null || holdings.isEmpty()) return;

        try {
            // 构造股票代码列表 (东财格式: 1.600519, 0.000568)
            List<String> secIds = new ArrayList<>();
            Map<String, String> codeToSecId = new HashMap<>();
            for (Map<String, Object> h : holdings) {
                String code = String.valueOf(h.get("stockCode"));
                String secId = formatStockCodeForApi(code);
                secIds.add(secId);
                codeToSecId.put(code, secId);
            }

            // 调用东财实时行情API: f2=现价, f3=涨跌幅, f12=代码, f14=名称, f100=行业
            String codes = String.join(",", secIds);
            String url = "https://push2.eastmoney.com/api/qt/ulist.np/get?"
                    + "fields=f2,f3,f12,f14,f100&secids=" + codes;
            String body = httpGetWithReferer(url);
            if (body == null) return;

            JsonNode root = objectMapper.readTree(body);
            JsonNode diffs = root.path("data").path("diff");
            if (!diffs.isArray()) return;

            // 构建 股票代码 → {涨跌幅, 当前价, 行业} 的映射
            Map<String, BigDecimal> codeToChange = new HashMap<>();
            Map<String, BigDecimal> codeToPrice = new HashMap<>();
            Map<String, String> codeToIndustry = new HashMap<>();
            for (JsonNode diff : diffs) {
                String stockCode = diff.path("f12").asText("");
                // 东财API返回的f3(涨跌幅)和f2(现价)都是*100后的整数值
                BigDecimal changePercent = parseBigDecimal(diff.path("f3").asText(""))
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                BigDecimal currentPrice = parseBigDecimal(diff.path("f2").asText(""))
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                String industry = diff.path("f100").asText("");
                if (!stockCode.isEmpty()) {
                    codeToChange.put(stockCode, changePercent);
                    codeToPrice.put(stockCode, currentPrice);
                    if (!industry.isEmpty() && !industry.equals("-")) {
                        codeToIndustry.put(stockCode, industry);
                    }
                }
            }

            // Task 7: 为每只持仓股票添加实时涨跌幅和当前价格
            // Task 3: 同时按行业聚合持仓比例
            // Task 5: 同时按行业加权计算板块涨幅
            Map<String, BigDecimal> industryRatioMap = new LinkedHashMap<>();
            Map<String, BigDecimal> industryWeightedChange = new LinkedHashMap<>();
            for (Map<String, Object> h : holdings) {
                String code = String.valueOf(h.get("stockCode"));
                BigDecimal change = codeToChange.getOrDefault(code, null);
                BigDecimal price = codeToPrice.getOrDefault(code, null);
                String industry = codeToIndustry.getOrDefault(code, null);

                if (change != null) h.put("changePercent", change);
                if (price != null && price.compareTo(BigDecimal.ZERO) > 0) h.put("currentPrice", price);
                if (industry != null) {
                    h.put("industry", industry);
                    BigDecimal ratio = toBigDecimal(h.get("ratio"));
                    industryRatioMap.merge(industry, ratio, BigDecimal::add);
                    if (change != null) {
                        industryWeightedChange.merge(industry, change.multiply(ratio), BigDecimal::add);
                    }
                }
            }

            // Task 3: 生成行业分布数据
            if (!industryRatioMap.isEmpty()) {
                List<Map<String, Object>> industryDist = new ArrayList<>();
                industryRatioMap.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                        .forEach(entry -> {
                            Map<String, Object> item = new HashMap<>();
                            item.put("industry", entry.getKey());
                            item.put("ratio", entry.getValue());
                            industryDist.add(item);
                        });
                dto.setIndustryDist(industryDist);
            }

            // Task 5: 基于持仓股票实时涨幅加权计算行业板块涨幅
            if (!industryWeightedChange.isEmpty()) {
                List<Map<String, Object>> sectorChanges = new ArrayList<>();
                industryRatioMap.entrySet().stream()
                        .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                        .forEach(entry -> {
                            String industry = entry.getKey();
                            BigDecimal totalRatio = entry.getValue();
                            BigDecimal weightedSum = industryWeightedChange.getOrDefault(industry, BigDecimal.ZERO);
                            BigDecimal avgChange = totalRatio.compareTo(BigDecimal.ZERO) > 0
                                    ? weightedSum.divide(totalRatio, 2, RoundingMode.HALF_UP)
                                    : BigDecimal.ZERO;
                            Map<String, Object> item = new HashMap<>();
                            item.put("sectorName", industry);
                            item.put("changePercent", avgChange);
                            sectorChanges.add(item);
                        });
                dto.setSectorChanges(sectorChanges);
            }

        } catch (Exception e) {
            log.warn("丰富持仓实时数据失败: code={}", dto.getCode(), e);
        }
    }

    private String formatStockCodeForApi(String code) {
        if (code == null) return "";
        code = code.trim();
        // A股代码固定6位: 6开头=沪市(1.), 0或3开头=深市(0.)
        if (code.length() == 6) {
            if (code.startsWith("6")) return "1." + code;
            if (code.startsWith("0") || code.startsWith("3")) return "0." + code;
        }
        // 港股: 4-5位数字, 使用116.前缀
        if (code.matches("\\d{4,5}")) return "116." + code;
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

    /**
     * 提取JS数组变量（支持嵌套对象）
     * 使用括号计数法正确提取完整的数组内容
     */
    private String extractJsArray(String js, String varName) {
        // 先找到变量声明的位置
        String prefix = "var " + varName + " = [";
        int startIndex = js.indexOf(prefix);
        if (startIndex < 0) {
            // 尝试带空格的格式
            prefix = "var " + varName + "=[";
            startIndex = js.indexOf(prefix);
            if (startIndex < 0) return null;
        }
        
        // 找到数组开始位置
        int arrayStart = js.indexOf('[', startIndex);
        if (arrayStart < 0) return null;
        
        // 使用括号计数找到数组结束位置
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        
        for (int i = arrayStart; i < js.length(); i++) {
            char c = js.charAt(i);
            
            if (escape) {
                escape = false;
                continue;
            }
            
            if (c == '\\' && inString) {
                escape = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (!inString) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return js.substring(arrayStart, i + 1);
                    }
                }
            }
        }
        
        return null;
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
            case "QDII", "QDII-指数", "QDII-股票", "QDII-混合" -> "QDII";
            case "指数型" -> "INDEX";
            default -> "MIXED";
        };
    }

    /**
     * 获取基金完整持仓（从年报/半年报）
     * 年报(Q4)和半年报(Q2)会披露完整持仓，季报(Q1/Q3)只有前10大重仓股
     * 此方法获取所有报告期的持仓表格，选择持仓数量最多的那个（通常是半年报/年报）
     *
     * @return 完整持仓列表，每项包含 stockCode 和 ratio
     */
    public List<Map<String, Object>> fetchAllHoldings(String fundCode) {
        try {
            String url = "https://fundf10.eastmoney.com/FundArchivesDatas.aspx?type=jjcc&code=" + fundCode + "&topline=1000";
            String body = httpGetWithReferer(url);
            if (body == null || !body.contains("<tbody>")) {
                log.warn("完整持仓数据为空: {}", fundCode);
                return Collections.emptyList();
            }

            // 按 <table> 标签分割所有表格（包含 thead 和 tbody）
            List<String> tables = new ArrayList<>();
            int searchFrom = 0;
            while (true) {
                int tableStart = body.indexOf("<table", searchFrom);
                if (tableStart < 0) break;
                int tableEnd = body.indexOf("</table>", tableStart);
                if (tableEnd < 0) break;
                tables.add(body.substring(tableStart, tableEnd + "</table>".length()));
                searchFrom = tableEnd + 1;
            }

            if (tables.isEmpty()) {
                return Collections.emptyList();
            }

            // 在每个表格中解析持仓，选取持仓数量最多的表格
            List<Map<String, Object>> bestHoldings = Collections.emptyList();
            for (int tableIdx = 0; tableIdx < tables.size(); tableIdx++) {
                String tableHtml = tables.get(tableIdx);
                List<Map<String, Object>> holdings = parseHoldingsTable(tableHtml, tableIdx);
                if (holdings.size() > bestHoldings.size()) {
                    bestHoldings = holdings;
                }
            }

            log.info("完整持仓获取: fund={}, 最佳表格持仓数={}", fundCode, bestHoldings.size());
            return bestHoldings;
        } catch (Exception e) {
            log.error("获取完整持仓失败: {}", fundCode, e);
            return Collections.emptyList();
        }
    }

    /**
     * 解析单个持仓表格的HTML
     * 表格0（最新季度）通常有9列：序号、代码、名称、最新价、涨跌幅、相关资讯、占净值比例、持股数、持仓市值
     * 其他表格（历史报告期）通常有7列：序号、代码、名称、占净值比例、持股数、持仓市值、(可能有其他列)
     * 通过查找"占净值比例"所在列来动态定位ratio
     */
    private List<Map<String, Object>> parseHoldingsTable(String tableHtml, int tableIdx) {
        List<Map<String, Object>> holdings = new ArrayList<>();
        try {
            // 动态检测列数和ratio列位置
            // 先找 <thead> 中的表头来确定 ratio 列索引
            int ratioColIndex = -1;
            Pattern thPattern = Pattern.compile("<th[^>]*>(.*?)</th>", Pattern.DOTALL);
            Matcher thMatcher = thPattern.matcher(tableHtml);
            int colIdx = 0;
            while (thMatcher.find()) {
                String headerText = thMatcher.group(1).replaceAll("<[^>]+>", "").trim();
                if (headerText.contains("占净值比例")) {
                    ratioColIndex = colIdx;
                    break;
                }
                colIdx++;
            }

            // 如果没有找到表头，根据表格索引使用默认值
            // 表格0: 9列，占净值比例在index 6
            // 其他表格: 7列，占净值比例在index 4
            if (ratioColIndex < 0) {
                ratioColIndex = (tableIdx == 0) ? 6 : 4;
            }

            // 解析每一行 — 使用字符串分割代替全局正则，避免大HTML上的回溯性能问题
            Pattern tdPattern = Pattern.compile("<td[^>]*>(.*?)</td>", Pattern.DOTALL);
            String[] rows = tableHtml.split("<tr>");
            for (String rowSegment : rows) {
                int trEnd = rowSegment.indexOf("</tr>");
                if (trEnd < 0) continue;
                String row = rowSegment.substring(0, trEnd);

                // 检查第一个 <td> 是否为数字序号
                Matcher firstTd = Pattern.compile("<td>(\\d+)</td>").matcher(row);
                if (!firstTd.find()) continue;

                Matcher tdMatcher = tdPattern.matcher(row);
                List<String> cells = new ArrayList<>();
                while (tdMatcher.find()) {
                    cells.add(tdMatcher.group(1).replaceAll("<[^>]+>", "").trim());
                }

                if (cells.size() > ratioColIndex && cells.size() >= 3) {
                    String stockCode = cells.get(1);
                    String ratioStr = cells.get(ratioColIndex);
                    BigDecimal ratio = parseBigDecimal(ratioStr);
                    if (ratio.compareTo(BigDecimal.ZERO) > 0 && !stockCode.isEmpty()) {
                        Map<String, Object> h = new HashMap<>();
                        h.put("stockCode", stockCode);
                        h.put("ratio", ratio);
                        holdings.add(h);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析持仓表格失败: tableIdx={}", tableIdx, e);
        }
        return holdings;
    }
}
