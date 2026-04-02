package com.qoder.fund.cli.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.qoder.fund.dto.*;
import com.qoder.fund.entity.Account;
import com.qoder.fund.entity.FundTransaction;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * CLI 表格格式化工具
 */
public class CliTableFormatter {

    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String BOLD = "\u001B[1m";

    private final ObjectMapper objectMapper;
    private final boolean useColor;
    private final boolean useJson;

    public CliTableFormatter(boolean useColor, boolean useJson) {
        this.useColor = useColor;
        this.useJson = useJson;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public String format(Object data) {
        if (useJson) {
            try {
                return objectMapper.writeValueAsString(data);
            } catch (JsonProcessingException e) {
                return "Error: " + e.getMessage();
            }
        }
        return data.toString();
    }

    public String formatFundSearch(List<FundSearchDTO> funds) {
        if (useJson) {
            return toJson(Map.of("list", funds));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("基金搜索结果")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-12s %-30s %-10s",
                "基金代码", "基金名称", "类型")).append("\n");
        sb.append(separator()).append("\n");

        for (FundSearchDTO fund : funds) {
            sb.append(String.format("%-12s %-30s %-10s",
                    fund.getCode(),
                    truncate(fund.getName(), 30),
                    fund.getType())).append("\n");
        }
        sb.append(separator()).append("\n");
        sb.append("共找到 ").append(funds.size()).append(" 只基金");
        return sb.toString();
    }

    public String formatFundDetail(FundDetailDTO detail) {
        if (useJson) {
            return toJson(detail);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("基金详情")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-15s %s", "基金代码:", detail.getCode())).append("\n");
        sb.append(String.format("%-15s %s", "基金名称:", detail.getName())).append("\n");
        sb.append(String.format("%-15s %s", "基金类型:", detail.getType())).append("\n");
        sb.append(String.format("%-15s %s", "管理公司:", detail.getCompany())).append("\n");
        sb.append(String.format("%-15s %s", "基金经理:", detail.getManager())).append("\n");
        sb.append(String.format("%-15s %s", "最新净值:", detail.getLatestNav())).append("\n");
        sb.append(String.format("%-15s %s", "净值日期:", detail.getLatestNavDate())).append("\n");

        if (detail.getEstimateNav() != null) {
            sb.append(separator()).append("\n");
            sb.append(cyan("实时估值")).append("\n");
            sb.append(String.format("%-15s %s", "估算净值:", detail.getEstimateNav())).append("\n");
            sb.append(String.format("%-15s %s", "估算涨幅:", formatDailyReturn(detail.getEstimateReturn()))).append("\n");
        }
        sb.append(separator());
        return sb.toString();
    }

    public String formatNavHistory(NavHistoryDTO history) {
        if (useJson) {
            return toJson(history);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("净值历史")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-12s %-12s", "日期", "净值")).append("\n");
        sb.append(separator()).append("\n");

        List<String> dates = history.getDates();
        List<BigDecimal> navs = history.getNavs();
        int count = Math.min(dates.size(), navs.size());

        // 只显示最近20条
        int start = Math.max(0, count - 20);
        for (int i = start; i < count; i++) {
            sb.append(String.format("%-12s %-12s", dates.get(i), navs.get(i))).append("\n");
        }
        sb.append(separator()).append("\n");
        if (count > 20) {
            sb.append("... (共 ").append(count).append(" 条记录, 显示最近 20 条)");
        } else {
            sb.append("共 ").append(count).append(" 条记录");
        }
        return sb.toString();
    }

    public String formatEstimates(EstimateSourceDTO estimates) {
        if (useJson) {
            return toJson(estimates);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("多源估值")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-15s %-12s %-12s %-10s", "数据源", "估算净值", "估算涨幅", "状态")).append("\n");
        sb.append(separator()).append("\n");

        for (EstimateSourceDTO.EstimateItem item : estimates.getSources()) {
            String status = item.isAvailable() ? green("可用") : red("不可用");
            String estimateReturn = item.getEstimateReturn() != null ?
                    formatDailyReturn(item.getEstimateReturn()) : "-";
            sb.append(String.format("%-15s %-12s %-12s %-10s",
                    item.getLabel(),
                    item.getEstimateNav() != null ? item.getEstimateNav().toString() : "-",
                    estimateReturn,
                    status)).append("\n");
        }
        sb.append(separator());
        return sb.toString();
    }

    public String formatPositions(List<PositionDTO> positions) {
        if (useJson) {
            return toJson(Map.of("list", positions));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("持仓列表")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-6s %-10s %-20s %-10s %-12s %-12s %-10s %-12s",
                "ID", "基金代码", "基金名称", "类型", "持仓份额", "成本金额", "市值", "收益")).append("\n");
        sb.append(separator()).append("\n");

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;

        for (PositionDTO p : positions) {
            String profitStr = formatProfit(p.getProfit());
            sb.append(String.format("%-6d %-10s %-20s %-10s %-12s %-12s %-10s %s",
                    p.getId(),
                    p.getFundCode(),
                    truncate(p.getFundName(), 20),
                    p.getFundType(),
                    p.getShares(),
                    p.getCostAmount(),
                    p.getMarketValue() != null ? p.getMarketValue() : "-",
                    profitStr)).append("\n");

            if (p.getMarketValue() != null) {
                totalMarketValue = totalMarketValue.add(p.getMarketValue());
            }
            if (p.getProfit() != null) {
                totalProfit = totalProfit.add(p.getProfit());
            }
            if (p.getCostAmount() != null) {
                totalCost = totalCost.add(p.getCostAmount());
            }
        }
        sb.append(separator()).append("\n");
        sb.append(String.format("%-6s %-10s %-20s %-10s %-12s %-12s %-10s %s",
                "", "", "", "", "", totalCost, totalMarketValue, formatProfit(totalProfit))).append("\n");
        sb.append(separator()).append("\n");
        sb.append("共 ").append(positions.size()).append(" 只持仓");
        return sb.toString();
    }

    public String formatWatchlist(Map<String, Object> watchlistData) {
        if (useJson) {
            return toJson(watchlistData);
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> list = (List<Map<String, Object>>) watchlistData.get("list");

        StringBuilder sb = new StringBuilder();
        sb.append(header("自选列表")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-6s %-10s %-25s %-10s %-12s %-10s",
                "ID", "基金代码", "基金名称", "类型", "最新净值", "日涨幅")).append("\n");
        sb.append(separator()).append("\n");

        for (Map<String, Object> item : list) {
            BigDecimal dailyReturn = (BigDecimal) item.get("dailyReturn");
            sb.append(String.format("%-6d %-10s %-25s %-10s %-12s %s",
                    item.get("id"),
                    item.get("fundCode"),
                    truncate((String) item.get("fundName"), 25),
                    item.get("fundType"),
                    item.get("latestNav") != null ? item.get("latestNav") : "-",
                    formatDailyReturn(dailyReturn))).append("\n");
        }
        sb.append(separator()).append("\n");
        sb.append("共 ").append(list.size()).append(" 只自选基金");
        return sb.toString();
    }

    public String formatDashboard(DashboardDTO dashboard) {
        if (useJson) {
            return toJson(dashboard);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("资产概览")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-20s %s", "总资产:", bold(dashboard.getTotalAsset().toString()))).append("\n");
        sb.append(String.format("%-20s %s", "总收益:", formatProfit(dashboard.getTotalProfit()))).append("\n");
        sb.append(String.format("%-20s %s", "收益率:", formatProfitRate(dashboard.getTotalProfitRate()))).append("\n");
        sb.append(String.format("%-20s %s", "今日收益:", formatProfit(dashboard.getTodayProfit()))).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-20s %d", "持仓数量:", dashboard.getPositions().size())).append("\n");
        sb.append(separator());
        return sb.toString();
    }

    public String formatProfitTrend(ProfitTrendDTO trend) {
        if (useJson) {
            return toJson(trend);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("收益趋势")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-12s %-15s", "日期", "收益")).append("\n");
        sb.append(separator()).append("\n");

        List<String> dates = trend.getDates();
        List<BigDecimal> profits = trend.getProfits();

        for (int i = 0; i < dates.size(); i++) {
            String profit = profits.get(i) != null ? formatProfit(profits.get(i)) : "-";
            sb.append(String.format("%-12s %-15s",
                    dates.get(i), profit)).append("\n");
        }
        sb.append(separator());
        return sb.toString();
    }

    public String formatAccounts(List<Account> accounts) {
        if (useJson) {
            return toJson(Map.of("list", accounts));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("账户列表")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-6s %-20s %-15s", "ID", "账户名称", "平台")).append("\n");
        sb.append(separator()).append("\n");

        for (Account account : accounts) {
            sb.append(String.format("%-6d %-20s %-15s",
                    account.getId(),
                    account.getName(),
                    account.getPlatform() != null ? account.getPlatform() : "-")).append("\n");
        }
        sb.append(separator()).append("\n");
        sb.append("共 ").append(accounts.size()).append(" 个账户");
        return sb.toString();
    }

    public String formatTransactions(List<FundTransaction> transactions) {
        if (useJson) {
            return toJson(Map.of("list", transactions));
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("交易记录")).append("\n");
        sb.append(separator()).append("\n");
        sb.append(String.format("%-6s %-12s %-10s %-12s %-12s %-10s",
                "ID", "交易日期", "类型", "份额", "金额", "价格")).append("\n");
        sb.append(separator()).append("\n");

        for (FundTransaction t : transactions) {
            String typeStr = switch (t.getType()) {
                case "BUY" -> green("买入");
                case "SELL" -> red("卖出");
                case "DIVIDEND" -> yellow("分红");
                default -> t.getType();
            };
            sb.append(String.format("%-6d %-12s %-10s %-12s %-12s %-10s",
                    t.getId(),
                    t.getTradeDate(),
                    typeStr,
                    t.getShares(),
                    t.getAmount(),
                    t.getPrice() != null ? t.getPrice().toString() : "-")).append("\n");
        }
        sb.append(separator()).append("\n");
        sb.append("共 ").append(transactions.size()).append(" 条记录");
        return sb.toString();
    }

    public String formatEstimateAnalysis(EstimateAnalysisDTO analysis) {
        if (useJson) {
            return toJson(analysis);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(header("数据源准确度分析")).append("\n");
        sb.append(separator()).append("\n");

        if (analysis.getCurrentEstimates() != null) {
            sb.append(cyan("当前估值")).append("\n");
            EstimateAnalysisDTO.CurrentEstimate ce = analysis.getCurrentEstimates();
            if (ce.getSmartEstimate() != null) {
                sb.append(String.format("%-15s %s", "智能估值:", ce.getSmartEstimate().getNav())).append("\n");
            }
            if (ce.getActualNav() != null) {
                sb.append(String.format("%-15s %s", "实际净值:", ce.getActualNav())).append("\n");
            }
            sb.append("\n");
        }

        if (analysis.getAccuracyStats() != null && analysis.getAccuracyStats().getSources() != null) {
            sb.append(cyan("准确度统计")).append("\n");
            sb.append(String.format("%-15s %-10s %-10s %-10s", "数据源", "MAE", "样本数", "评级")).append("\n");
            sb.append(separator()).append("\n");
            for (EstimateAnalysisDTO.SourceAccuracy stats : analysis.getAccuracyStats().getSources()) {
                sb.append(String.format("%-15s %-10s %-10d %s",
                        stats.getLabel(),
                        stats.getMae(),
                        stats.getPredictionCount(),
                        formatRating(stats.getRating()))).append("\n");
            }
        }
        sb.append(separator());
        return sb.toString();
    }

    public String formatSuccess(String message) {
        return useColor ? GREEN + "✓ " + message + RESET : "✓ " + message;
    }

    public String formatError(String message) {
        return useColor ? RED + "✗ " + message + RESET : "✗ " + message;
    }

    // Helper methods

    private String formatDailyReturn(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        String formatted = value.toString() + "%";
        if (!useColor) {
            return formatted;
        }
        int cmp = value.compareTo(BigDecimal.ZERO);
        if (cmp > 0) {
            return RED + formatted + RESET;
        }
        if (cmp < 0) {
            return GREEN + formatted + RESET;
        }
        return formatted;
    }

    private String formatProfit(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        String formatted = value.toString();
        if (!useColor) {
            return formatted;
        }
        int cmp = value.compareTo(BigDecimal.ZERO);
        if (cmp > 0) {
            return RED + formatted + RESET;
        }
        if (cmp < 0) {
            return GREEN + formatted + RESET;
        }
        return formatted;
    }

    private String formatProfitRate(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        String formatted = value.toString() + "%";
        if (!useColor) {
            return formatted;
        }
        int cmp = value.compareTo(BigDecimal.ZERO);
        if (cmp > 0) {
            return RED + formatted + RESET;
        }
        if (cmp < 0) {
            return GREEN + formatted + RESET;
        }
        return formatted;
    }

    private String formatRating(Integer rating) {
        if (rating == null) {
            return "-";
        }
        String stars = "★".repeat(rating) + "☆".repeat(5 - rating);
        if (!useColor) {
            return stars;
        }
        if (rating >= 4) {
            return GREEN + stars + RESET;
        }
        if (rating >= 3) {
            return YELLOW + stars + RESET;
        }
        return RED + stars + RESET;
    }

    private String header(String text) {
        if (!useColor) {
            return text;
        }
        return BOLD + CYAN + text + RESET;
    }

    private String separator() {
        return "-".repeat(80);
    }

    private String red(String text) {
        return useColor ? RED + text + RESET : text;
    }

    private String green(String text) {
        return useColor ? GREEN + text + RESET : text;
    }

    private String yellow(String text) {
        return useColor ? YELLOW + text + RESET : text;
    }

    private String cyan(String text) {
        return useColor ? CYAN + text + RESET : text;
    }

    private String bold(String text) {
        return useColor ? BOLD + text + RESET : text;
    }

    private String truncate(String str, int maxLength) {
        if (str == null) {
            return "";
        }
        if (str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength - 3) + "...";
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "Error: " + e.getMessage();
        }
    }
}
