package com.qoder.fund.cli;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import picocli.CommandLine;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * 基金管家 CLI 启动器
 *
 * 使用方式:
 * 1. Web 模式: ./mvnw spring-boot:run (默认)
 * 2. CLI 模式: java -jar target/fund-*-cli.jar <command>
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.qoder.fund")
@EnableScheduling
public class FundCliApplication implements CommandLineRunner, ExitCodeGenerator {

    private final CommandLine.IFactory factory;
    private final FundCommand fundCommand;
    private final PositionCommand positionCommand;
    private final WatchlistCommand watchlistCommand;
    private final DashboardCommand dashboardCommand;
    private final AccountCommand accountCommand;
    private final SyncCommand syncCommand;
    private final ReportCommand reportCommand;
    private final DataSource dataSource;

    private int exitCode;

    public FundCliApplication(
            CommandLine.IFactory factory,
            FundCommand fundCommand,
            PositionCommand positionCommand,
            WatchlistCommand watchlistCommand,
            DashboardCommand dashboardCommand,
            AccountCommand accountCommand,
            SyncCommand syncCommand,
            ReportCommand reportCommand,
            DataSource dataSource) {
        this.factory = factory;
        this.fundCommand = fundCommand;
        this.positionCommand = positionCommand;
        this.watchlistCommand = watchlistCommand;
        this.dashboardCommand = dashboardCommand;
        this.accountCommand = accountCommand;
        this.syncCommand = syncCommand;
        this.reportCommand = reportCommand;
        this.dataSource = dataSource;
    }

    public static void main(String[] args) {
        // 检查是否需要启动 Web 服务器
        boolean webMode = false;
        for (String arg : args) {
            if (arg.equals("--web") || arg.equals("-w")) {
                webMode = true;
                break;
            }
        }

        SpringApplication app = new SpringApplication(FundCliApplication.class);

        if (webMode) {
            // Web 模式: 移除 --web 参数后启动
            String[] filteredArgs = java.util.Arrays.stream(args)
                    .filter(arg -> !arg.equals("--web") && !arg.equals("-w"))
                    .toArray(String[]::new);
            app.setWebApplicationType(WebApplicationType.SERVLET);
            app.run(filteredArgs);
        } else {
            // CLI 模式: 非 Web 应用，激活 cli profile 禁用缓存
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setAdditionalProfiles("cli");
            System.exit(SpringApplication.exit(app.run(args)));
        }
    }

    @Override
    public void run(String... args) throws Exception {
        // CLI 启动时检查数据库连接
        if (!checkDatabaseConnection()) {
            System.err.println("错误: 无法连接到数据库，请检查:");
            System.err.println("  1. MySQL 服务是否已启动");
            System.err.println("  2. 数据库配置是否正确 (DB_USERNAME, DB_PASSWORD 环境变量)");
            System.err.println("  3. 数据库 fund_manager 是否存在");
            this.exitCode = 1;
            return;
        }

        // 创建主命令
        CommandLine commandLine = new CommandLine(new MainCommand(), factory);

        // 注册子命令
        commandLine.addSubcommand("fund", fundCommand);
        commandLine.addSubcommand("position", positionCommand);
        commandLine.addSubcommand("watchlist", watchlistCommand);
        commandLine.addSubcommand("dashboard", dashboardCommand);
        commandLine.addSubcommand("account", accountCommand);
        commandLine.addSubcommand("sync", syncCommand);
        commandLine.addSubcommand("report", reportCommand);

        // 设置命令名称
        commandLine.setCommandName("fund-cli");

        // 执行命令
        exitCode = commandLine.execute(args);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }

    /**
     * 检查数据库连接是否可用
     */
    private boolean checkDatabaseConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (SQLException e) {
            System.err.println("数据库连接检查失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 主命令定义
     */
    @CommandLine.Command(
            name = "fund-cli",
            description = "基金管家 - 命令行工具",
            mixinStandardHelpOptions = true,
            version = "基金管家 CLI 1.0",
            subcommands = {}
    )
    static class MainCommand implements Runnable {

        @CommandLine.Spec
        private CommandLine.Model.CommandSpec spec;

        @Override
        public void run() {
            // 打印欢迎信息
            System.out.println();
            System.out.println("╔════════════════════════════════════════════════════════╗");
            System.out.println("║              基金管家 - 命令行工具 v1.0                 ║");
            System.out.println("╠════════════════════════════════════════════════════════╣");
            System.out.println("║  一站式基金数据聚合管理工具                             ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
            System.out.println();

            // 打印可用命令
            System.out.println("可用命令:");
            System.out.println();
            System.out.println("  fund        基金查询与管理");
            System.out.println("              fund search <keyword>     搜索基金");
            System.out.println("              fund detail <code>        查看基金详情");
            System.out.println("              fund nav <code>           查看净值历史");
            System.out.println("              fund estimate <code>      查看实时估值");
            System.out.println("              fund refresh <code>       刷新基金数据");
            System.out.println("              fund analysis <code>      数据源准确度分析");
            System.out.println();
            System.out.println("  position    持仓管理");
            System.out.println("              position list             列出所有持仓");
            System.out.println("              position add              添加持仓");
            System.out.println("              position delete <id>      删除持仓");
            System.out.println("              position buy <id>         买入加仓");
            System.out.println("              position sell <id>        卖出减仓");
            System.out.println("              position transactions <id> 查看交易记录");
            System.out.println();
            System.out.println("  watchlist   自选基金管理");
            System.out.println("              watchlist list            列出所有自选");
            System.out.println("              watchlist add <code>      添加自选");
            System.out.println("              watchlist remove <id>     删除自选");
            System.out.println();
            System.out.println("  dashboard   资产概览");
            System.out.println("              dashboard                 查看资产概览");
            System.out.println("              dashboard trend           查看收益趋势");
            System.out.println();
            System.out.println("  account     账户管理");
            System.out.println("              account list              列出所有账户");
            System.out.println("              account create            创建账户");
            System.out.println("              account delete <id>       删除账户");
            System.out.println();
            System.out.println("  sync        数据同步（定时任务/手动触发）");
            System.out.println("              sync nav                  同步当日净值");
            System.out.println("              sync estimate             快照估值预测");
            System.out.println("              sync holdings             同步重仓股数据");
            System.out.println("              sync evaluate             评估预测准确度");
            System.out.println("              sync compensate           补偿缺失历史数据");
            System.out.println("              sync all                  执行全部同步任务");
            System.out.println();
            System.out.println("  report      数据分析报告（面向外部 Agent）");
            System.out.println("              report market                 市场概览（大盘+板块）");
            System.out.println("              report diagnose <code>        单只基金诊断数据");
            System.out.println("              report risk                   持仓风险分析报告");
            System.out.println("              report positions              持仓客观指标数据");
            System.out.println();
            System.out.println("全局选项:");
            System.out.println("  --no-color  禁用颜色输出");
            System.out.println("  --json      以 JSON 格式输出");
            System.out.println("  -h, --help  显示帮助信息");
            System.out.println();
            System.out.println("示例:");
            System.out.println("  fund-cli fund search 白酒");
            System.out.println("  fund-cli fund detail 161725");
            System.out.println("  fund-cli position list --json");
            System.out.println("  fund-cli dashboard --no-color");
            System.out.println();

            spec.commandLine().usage(System.out);
        }
    }
}
