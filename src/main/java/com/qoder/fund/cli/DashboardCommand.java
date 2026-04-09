package com.qoder.fund.cli;

import com.qoder.fund.cli.util.CliTableFormatter;
import com.qoder.fund.dto.DashboardDTO;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.dto.ProfitTrendDTO;
import com.qoder.fund.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 仪表盘相关 CLI 命令
 */
@Component
@CommandLine.Command(
        name = "dashboard",
        aliases = {"dash", "db"},
        description = "资产概览与统计命令",
        subcommands = {
                DashboardCommand.OverviewCommand.class,
                DashboardCommand.TrendCommand.class,
                DashboardCommand.BroadcastCommand.class
        }
)
@RequiredArgsConstructor
public class DashboardCommand implements Callable<Integer> {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
    private boolean noColor;

    @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
    private boolean json;

    private final DashboardService dashboardService;

    @Override
    public Integer call() {
        // 默认执行 overview 命令
        CliTableFormatter formatter = new CliTableFormatter(!noColor, json);
        DashboardDTO dashboard = dashboardService.getDashboard();
        System.out.println(formatter.formatDashboard(dashboard));
        return 0;
    }

    /**
     * 资产概览命令
     */
    @Component
    @CommandLine.Command(name = "overview", aliases = {"ov", "summary"}, description = "查看资产概览")
    @RequiredArgsConstructor
    public static class OverviewCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final DashboardService dashboardService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            DashboardDTO dashboard = dashboardService.getDashboard();
            System.out.println(formatter.formatDashboard(dashboard));
            return 0;
        }
    }

    /**
     * 收益趋势命令
     */
    @Component
    @CommandLine.Command(name = "trend", aliases = {"tr", "profit"}, description = "查看收益趋势")
    @RequiredArgsConstructor
    public static class TrendCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"-d", "--days"}, defaultValue = "7", description = "天数 (默认: 7)")
        private int days;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final DashboardService dashboardService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            ProfitTrendDTO trend = dashboardService.getProfitTrend(days);
            System.out.println(formatter.formatProfitTrend(trend));
            return 0;
        }
    }

    /**
     * 收益播报命令
     * 用于定时任务播报今日预估收益，输出格式简洁，适合外部 agent 解析和语音播报
     */
    @Component
    @CommandLine.Command(
            name = "broadcast",
            aliases = {"bc", "report"},
            description = "今日预估收益播报（适合定时任务和语音播报）"
    )
    @RequiredArgsConstructor
    public static class BroadcastCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出（适合程序解析）")
        private boolean json;

        @CommandLine.Option(names = {"--brief"}, description = "简洁模式（仅输出关键数据）")
        private boolean brief;

        @CommandLine.Option(names = {"--positions"}, description = "包含持仓明细")
        private boolean includePositions;

        private final DashboardService dashboardService;

        @Override
        public Integer call() {
            DashboardDTO dashboard = dashboardService.getDashboard();

            if (json) {
                System.out.println(formatJson(dashboard));
            } else if (brief) {
                System.out.println(formatBrief(dashboard));
            } else {
                System.out.println(formatBroadcast(dashboard));
            }
            return 0;
        }

        private String formatJson(DashboardDTO d) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            sb.append("  \"date\": \"").append(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append("\",\n");
            sb.append("  \"totalAsset\": ").append(d.getTotalAsset()).append(",\n");
            sb.append("  \"totalProfit\": ").append(d.getTotalProfit()).append(",\n");
            sb.append("  \"totalProfitRate\": ").append(d.getTotalProfitRate()).append(",\n");
            sb.append("  \"todayEstimateProfit\": ").append(d.getTodayEstimateProfit()).append(",\n");
            sb.append("  \"todayEstimateReturn\": ").append(d.getTodayEstimateReturn()).append(",\n");
            sb.append("  \"todayProfitIsEstimate\": ").append(d.getTodayProfitIsEstimate()).append(",\n");
            sb.append("  \"positionCount\": ").append(d.getPositions() != null ? d.getPositions().size() : 0).append("\n");
            sb.append("}");
            return sb.toString();
        }

        private String formatBrief(DashboardDTO d) {
            String profit = formatMoney(d.getTodayEstimateProfit());
            String rate = formatPercent(d.getTodayEstimateReturn());
            String status = d.getTodayProfitIsEstimate() ? "预估" : "实际";
            return String.format("今日%s收益: %s (%s)", status, profit, rate);
        }

        private String formatBroadcast(DashboardDTO d) {
            StringBuilder sb = new StringBuilder();
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("MM月dd日"));
            String profit = formatMoney(d.getTodayEstimateProfit());
            String rate = formatPercent(d.getTodayEstimateReturn());
            String status = d.getTodayProfitIsEstimate() ? "预估" : "实际";

            // 标题
            sb.append("══════════════════════════════════════\n");
            sb.append("  ").append(date).append(" 基金收益播报\n");
            sb.append("══════════════════════════════════════\n\n");

            // 总资产
            sb.append("📊 总资产: ").append(formatMoney(d.getTotalAsset())).append("\n");
            sb.append("📈 累计收益: ").append(formatMoney(d.getTotalProfit()));
            sb.append(" (").append(formatPercent(d.getTotalProfitRate())).append(")\n\n");

            // 今日预估
            sb.append("────────────────────────────────────────\n");
            sb.append("📅 今日").append(status).append("收益\n");
            sb.append("────────────────────────────────────────\n");
            sb.append("   金额: ").append(profit).append("\n");
            sb.append("   涨幅: ").append(rate).append("\n\n");

            // 持仓表现 - 区分"已验证"和"待验证"
            if (d.getPositions() != null && !d.getPositions().isEmpty()) {
                // 分组：已验证（有实际净值且非延迟） vs 待验证（无实际净值或延迟）
                List<PositionDTO> verifiedPositions = new ArrayList<>();
                List<PositionDTO> pendingPositions = new ArrayList<>();

                for (PositionDTO p : d.getPositions()) {
                    boolean isVerified = p.getActualReturn() != null
                            && !Boolean.TRUE.equals(p.getActualReturnDelayed());
                    if (isVerified) {
                        verifiedPositions.add(p);
                    } else {
                        pendingPositions.add(p);
                    }
                }

                // 已验证持仓
                if (!verifiedPositions.isEmpty()) {
                    sb.append("────────────────────────────────────────\n");
                    sb.append("📋 持仓表现（已验证）\n");
                    sb.append("────────────────────────────────────────\n");

                    for (PositionDTO p : verifiedPositions) {
                        appendPositionLine(sb, p, false);
                    }
                }

                // 待验证持仓（QDII等）
                if (!pendingPositions.isEmpty()) {
                    sb.append("\n────────────────────────────────────────\n");
                    sb.append("⏳ 待验证持仓（QDII延迟）\n");
                    sb.append("────────────────────────────────────────\n");

                    for (PositionDTO p : pendingPositions) {
                        appendPositionLine(sb, p, true);
                    }
                }
            }

            sb.append("\n══════════════════════════════════════\n");
            sb.append("  数据仅供参考，不构成投资建议\n");
            sb.append("══════════════════════════════════════\n");

            return sb.toString();
        }

        /**
         * 追加单行持仓信息
         * @param isPending 是否为待验证持仓
         */
        private void appendPositionLine(StringBuilder sb, PositionDTO p, boolean isPending) {
            boolean isQdii = "QDII".equals(p.getFundType());
            boolean isUsQdii = isUsQdiiFund(p.getFundName());

            if (isPending && isUsQdii) {
                // 纯美股 QDII：不报具体涨跌，说明待验证
                sb.append(String.format("  🔮 %-20s 预估待验证\n",
                        truncate(p.getFundName(), 20)));
                if (includePositions) {
                    sb.append("     💡 美股刚开盘，预估数据待后续净值回填验证\n");
                }
            } else if (isPending && isQdii) {
                // 其他 QDII：报预估涨跌，但标注待验证
                String estimateReturn = p.getEstimateReturn() != null
                        ? formatPercent(p.getEstimateReturn()) : "--";
                String profitSign = p.getEstimateReturn() != null && p.getEstimateReturn().compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                sb.append(String.format("  %s %-20s %s *\n", profitSign,
                        truncate(p.getFundName(), 20), estimateReturn));
                if (includePositions) {
                    sb.append("     💡 净值延迟发布，预估待后续验证\n");
                }
            } else {
                // 已验证持仓：正常报涨跌
                String estimateReturn = p.getEstimateReturn() != null
                        ? formatPercent(p.getEstimateReturn()) : "--";
                String profitSign = p.getEstimateReturn() != null && p.getEstimateReturn().compareTo(BigDecimal.ZERO) >= 0 ? "📈" : "📉";
                sb.append(String.format("  %s %-20s %s\n", profitSign,
                        truncate(p.getFundName(), 20), estimateReturn));

                if (includePositions) {
                    sb.append(String.format("     市值: %s | 持有收益: %s\n",
                            formatMoney(p.getMarketValue()),
                            formatMoney(p.getProfit())));
                }
            }
        }

        /**
         * 判断是否为纯美股 QDII 基金（通过名称关键词）
         */
        private boolean isUsQdiiFund(String fundName) {
            if (fundName == null) return false;
            String name = fundName.toLowerCase();
            // 美股关键词：纳斯达克、标普、道琼斯、美股、纳指等
            return name.contains("纳斯达克") || name.contains("纳指")
                    || name.contains("标普") || name.contains("s&p")
                    || name.contains("道琼斯") || name.contains("美股")
                    || name.contains("纳斯") || name.contains("qdii纳斯");
        }

        private String formatMoney(BigDecimal amount) {
            if (amount == null) {
                return "¥0.00";
            }
            return "¥" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        private String formatPercent(BigDecimal rate) {
            if (rate == null) {
                return "0.00%";
            }
            String sign = rate.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
            return sign + rate.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
        }

        private String truncate(String str, int maxLen) {
            if (str == null) {
                return "";
            }
            return str.length() > maxLen ? str.substring(0, maxLen) : str;
        }
    }
}
