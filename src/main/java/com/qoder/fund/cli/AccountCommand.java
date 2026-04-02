package com.qoder.fund.cli;

import com.qoder.fund.cli.util.CliTableFormatter;
import com.qoder.fund.entity.Account;
import com.qoder.fund.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * 账户相关 CLI 命令
 */
@Component
@CommandLine.Command(
        name = "account",
        aliases = {"acc"},
        description = "账户管理命令",
        subcommands = {
                AccountCommand.ListCommand.class,
                AccountCommand.CreateCommand.class,
                AccountCommand.DeleteCommand.class
        }
)
@RequiredArgsConstructor
public class AccountCommand implements Callable<Integer> {

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
     * 列出账户命令
     */
    @Component
    @CommandLine.Command(name = "list", aliases = {"ls"}, description = "列出所有账户")
    @RequiredArgsConstructor
    public static class ListCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final AccountService accountService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            List<Account> accounts = accountService.list();
            if (accounts.isEmpty()) {
                System.out.println("暂无账户");
                return 0;
            }

            System.out.println(formatter.formatAccounts(accounts));
            return 0;
        }
    }

    /**
     * 创建账户命令
     */
    @Component
    @CommandLine.Command(name = "create", aliases = {"add", "new"}, description = "创建新账户")
    @RequiredArgsConstructor
    public static class CreateCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"-n", "--name"}, required = true, description = "账户名称")
        private String name;

        @CommandLine.Option(names = {"-p", "--platform"}, description = "平台 (如: 支付宝, 天天基金)")
        private String platform;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        private final AccountService accountService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, false);

            try {
                accountService.create(name, platform);
                String msg = "账户创建成功: " + name;
                if (platform != null && !platform.isEmpty()) {
                    msg += " (平台: " + platform + ")";
                }
                System.out.println(formatter.formatSuccess(msg));
                return 0;
            } catch (Exception e) {
                System.err.println(formatter.formatError("创建失败: " + e.getMessage()));
                return 1;
            }
        }
    }

    /**
     * 删除账户命令
     */
    @Component
    @CommandLine.Command(name = "delete", aliases = {"rm", "del", "remove"}, description = "删除账户")
    @RequiredArgsConstructor
    public static class DeleteCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<id>", description = "账户ID")
        private Long id;

        @CommandLine.Option(names = {"--force", "-f"}, description = "强制删除，不提示确认")
        private boolean force;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        private final AccountService accountService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, false);

            if (!force) {
                System.out.print("确认删除账户 " + id + "? 该账户下的持仓也将被删除! [y/N]: ");
                java.util.Scanner scanner = new java.util.Scanner(System.in);
                String input = scanner.nextLine().trim().toLowerCase();
                if (!input.equals("y") && !input.equals("yes")) {
                    System.out.println("已取消删除");
                    return 0;
                }
            }

            try {
                accountService.delete(id);
                System.out.println(formatter.formatSuccess("账户删除成功: " + id));
                return 0;
            } catch (Exception e) {
                System.err.println(formatter.formatError("删除失败: " + e.getMessage()));
                return 1;
            }
        }
    }
}
