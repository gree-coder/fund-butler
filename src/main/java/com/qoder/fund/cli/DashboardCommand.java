package com.qoder.fund.cli;

import com.qoder.fund.cli.util.CliTableFormatter;
import com.qoder.fund.dto.DashboardDTO;
import com.qoder.fund.dto.ProfitTrendDTO;
import com.qoder.fund.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

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
                DashboardCommand.TrendCommand.class
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
}
