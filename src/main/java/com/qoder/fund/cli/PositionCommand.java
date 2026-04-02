package com.qoder.fund.cli;

import com.qoder.fund.cli.util.CliTableFormatter;
import com.qoder.fund.dto.PositionDTO;
import com.qoder.fund.dto.request.AddPositionRequest;
import com.qoder.fund.dto.request.AddTransactionRequest;
import com.qoder.fund.entity.FundTransaction;
import com.qoder.fund.service.PositionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * 持仓相关 CLI 命令
 */
@Component
@CommandLine.Command(
        name = "position",
        aliases = {"pos"},
        description = "持仓管理命令",
        subcommands = {
                PositionCommand.ListCommand.class,
                PositionCommand.AddCommand.class,
                PositionCommand.DeleteCommand.class,
                PositionCommand.TransactionsCommand.class,
                PositionCommand.BuyCommand.class,
                PositionCommand.SellCommand.class
        }
)
@RequiredArgsConstructor
public class PositionCommand implements Callable<Integer> {

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
     * 列出持仓命令
     */
    @Component
    @CommandLine.Command(name = "list", aliases = {"ls"}, description = "列出所有持仓")
    @RequiredArgsConstructor
    public static class ListCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"-a", "--account-id"}, description = "按账户筛选")
        private Long accountId;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final PositionService positionService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            List<PositionDTO> positions = positionService.list(accountId);
            if (positions.isEmpty()) {
                System.out.println("暂无持仓");
                return 0;
            }

            System.out.println(formatter.formatPositions(positions));
            return 0;
        }
    }

    /**
     * 添加持仓命令
     */
    @Component
    @CommandLine.Command(name = "add", description = "添加持仓")
    @RequiredArgsConstructor
    public static class AddCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"-c", "--code"}, required = true, description = "基金代码")
        private String code;

        @CommandLine.Option(names = {"-s", "--shares"}, description = "持仓份额")
        private BigDecimal shares;

        @CommandLine.Option(names = {"-a", "--amount"}, required = true, description = "买入金额")
        private BigDecimal amount;

        @CommandLine.Option(names = {"-p", "--price"}, description = "买入价格")
        private BigDecimal price;

        @CommandLine.Option(names = {"--account-id"}, description = "账户ID")
        private Long accountId;

        @CommandLine.Option(names = {"-d", "--date"}, description = "交易日期 (yyyy-MM-dd)")
        private String date;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        private final PositionService positionService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, false);

            AddPositionRequest request = new AddPositionRequest();
            request.setFundCode(code);
            request.setShares(shares);
            request.setAmount(amount);
            request.setPrice(price);
            request.setAccountId(accountId);
            request.setTradeDate(date != null ? LocalDate.parse(date) : LocalDate.now());

            try {
                positionService.add(request);
                System.out.println(formatter.formatSuccess("持仓添加成功: " + code));
                return 0;
            } catch (Exception e) {
                System.err.println(formatter.formatError("添加失败: " + e.getMessage()));
                return 1;
            }
        }
    }

    /**
     * 删除持仓命令
     */
    @Component
    @CommandLine.Command(name = "delete", aliases = {"rm", "del"}, description = "删除持仓")
    @RequiredArgsConstructor
    public static class DeleteCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<id>", description = "持仓ID")
        private Long id;

        @CommandLine.Option(names = {"--force", "-f"}, description = "强制删除，不提示确认")
        private boolean force;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        private final PositionService positionService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, false);

            if (!force) {
                System.out.print("确认删除持仓 " + id + "? [y/N]: ");
                java.util.Scanner scanner = new java.util.Scanner(System.in);
                String input = scanner.nextLine().trim().toLowerCase();
                if (!input.equals("y") && !input.equals("yes")) {
                    System.out.println("已取消删除");
                    return 0;
                }
            }

            try {
                positionService.delete(id);
                System.out.println(formatter.formatSuccess("持仓删除成功: " + id));
                return 0;
            } catch (Exception e) {
                System.err.println(formatter.formatError("删除失败: " + e.getMessage()));
                return 1;
            }
        }
    }

    /**
     * 查看交易记录命令
     */
    @Component
    @CommandLine.Command(name = "transactions", aliases = {"tx"}, description = "查看持仓交易记录")
    @RequiredArgsConstructor
    public static class TransactionsCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<id>", description = "持仓ID")
        private Long id;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final PositionService positionService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            List<FundTransaction> transactions = positionService.getTransactions(id);
            if (transactions.isEmpty()) {
                System.out.println("暂无交易记录");
                return 0;
            }

            System.out.println(formatter.formatTransactions(transactions));
            return 0;
        }
    }

    /**
     * 买入命令
     */
    @Component
    @CommandLine.Command(name = "buy", description = "买入基金（加仓）")
    @RequiredArgsConstructor
    public static class BuyCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<id>", description = "持仓ID")
        private Long id;

        @CommandLine.Option(names = {"-s", "--shares"}, required = true, description = "买入份额")
        private BigDecimal shares;

        @CommandLine.Option(names = {"-a", "--amount"}, required = true, description = "买入金额")
        private BigDecimal amount;

        @CommandLine.Option(names = {"-p", "--price"}, required = true, description = "成交价格")
        private BigDecimal price;

        @CommandLine.Option(names = {"-d", "--date"}, description = "交易日期 (yyyy-MM-dd)")
        private String date;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        private final PositionService positionService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, false);

            AddTransactionRequest request = new AddTransactionRequest();
            request.setType("BUY");
            request.setShares(shares);
            request.setAmount(amount);
            request.setPrice(price);
            request.setTradeDate(date != null ? LocalDate.parse(date) : LocalDate.now());

            try {
                positionService.addTransaction(id, request);
                System.out.println(formatter.formatSuccess("买入成功: 持仓 " + id));
                return 0;
            } catch (Exception e) {
                System.err.println(formatter.formatError("买入失败: " + e.getMessage()));
                return 1;
            }
        }
    }

    /**
     * 卖出命令
     */
    @Component
    @CommandLine.Command(name = "sell", description = "卖出基金（减仓）")
    @RequiredArgsConstructor
    public static class SellCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<id>", description = "持仓ID")
        private Long id;

        @CommandLine.Option(names = {"-s", "--shares"}, required = true, description = "卖出份额")
        private BigDecimal shares;

        @CommandLine.Option(names = {"-a", "--amount"}, required = true, description = "卖出金额")
        private BigDecimal amount;

        @CommandLine.Option(names = {"-p", "--price"}, required = true, description = "成交价格")
        private BigDecimal price;

        @CommandLine.Option(names = {"-d", "--date"}, description = "交易日期 (yyyy-MM-dd)")
        private String date;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        private final PositionService positionService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, false);

            AddTransactionRequest request = new AddTransactionRequest();
            request.setType("SELL");
            request.setShares(shares);
            request.setAmount(amount);
            request.setPrice(price);
            request.setTradeDate(date != null ? LocalDate.parse(date) : LocalDate.now());

            try {
                positionService.addTransaction(id, request);
                System.out.println(formatter.formatSuccess("卖出成功: 持仓 " + id));
                return 0;
            } catch (Exception e) {
                System.err.println(formatter.formatError("卖出失败: " + e.getMessage()));
                return 1;
            }
        }
    }
}
