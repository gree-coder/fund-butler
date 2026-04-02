package com.qoder.fund.cli;

import com.qoder.fund.cli.util.CliTableFormatter;
import com.qoder.fund.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 自选基金相关 CLI 命令
 */
@Component
@CommandLine.Command(
        name = "watchlist",
        aliases = {"wl", "watch"},
        description = "自选基金管理命令",
        subcommands = {
                WatchlistCommand.ListCommand.class,
                WatchlistCommand.AddCommand.class,
                WatchlistCommand.RemoveCommand.class
        }
)
@RequiredArgsConstructor
public class WatchlistCommand implements Callable<Integer> {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
    private boolean noColor;

    @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
    private boolean json;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }

    /**
     * 列出自选命令
     */
    @Component
    @CommandLine.Command(name = "list", aliases = {"ls"}, description = "列出所有自选基金")
    @RequiredArgsConstructor
    public static class ListCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"-g", "--group"}, description = "按分组筛选")
        private String group;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final WatchlistService watchlistService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            Map<String, Object> result = watchlistService.list(group);
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> list = (java.util.List<Map<String, Object>>) result.get("list");

            if (list.isEmpty()) {
                System.out.println("暂无自选基金");
                return 0;
            }

            System.out.println(formatter.formatWatchlist(result));
            return 0;
        }
    }

    /**
     * 添加自选命令
     */
    @Component
    @CommandLine.Command(name = "add", description = "添加基金到自选")
    @RequiredArgsConstructor
    public static class AddCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<code>", description = "基金代码")
        private String code;

        @CommandLine.Option(names = {"-g", "--group"}, description = "分组名称")
        private String group;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        private final WatchlistService watchlistService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, false);

            try {
                watchlistService.add(code, group);
                String msg = "自选添加成功: " + code;
                if (group != null && !group.isEmpty()) {
                    msg += " (分组: " + group + ")";
                }
                System.out.println(formatter.formatSuccess(msg));
                return 0;
            } catch (Exception e) {
                System.err.println(formatter.formatError("添加失败: " + e.getMessage()));
                return 1;
            }
        }
    }

    /**
     * 删除自选命令
     */
    @Component
    @CommandLine.Command(name = "remove", aliases = {"rm", "del", "delete"}, description = "从自选删除基金")
    @RequiredArgsConstructor
    public static class RemoveCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<id>", description = "自选记录ID")
        private Long id;

        @CommandLine.Option(names = {"--force", "-f"}, description = "强制删除，不提示确认")
        private boolean force;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        private final WatchlistService watchlistService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, false);

            if (!force) {
                System.out.print("确认从自选删除 " + id + "? [y/N]: ");
                java.util.Scanner scanner = new java.util.Scanner(System.in);
                String input = scanner.nextLine().trim().toLowerCase();
                if (!input.equals("y") && !input.equals("yes")) {
                    System.out.println("已取消删除");
                    return 0;
                }
            }

            try {
                watchlistService.remove(id);
                System.out.println(formatter.formatSuccess("自选删除成功: " + id));
                return 0;
            } catch (Exception e) {
                System.err.println(formatter.formatError("删除失败: " + e.getMessage()));
                return 1;
            }
        }
    }
}
