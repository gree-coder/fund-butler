package com.qoder.fund.cli;

import com.qoder.fund.cli.util.CliTableFormatter;
import com.qoder.fund.dto.*;
import com.qoder.fund.service.EstimateAnalysisService;
import com.qoder.fund.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * 基金相关 CLI 命令
 */
@Component
@CommandLine.Command(
        name = "fund",
        description = "基金查询与管理命令",
        subcommands = {
                FundCommand.SearchCommand.class,
                FundCommand.DetailCommand.class,
                FundCommand.NavHistoryCommand.class,
                FundCommand.EstimateCommand.class,
                FundCommand.RefreshCommand.class,
                FundCommand.AnalysisCommand.class
        }
)
@RequiredArgsConstructor
public class FundCommand implements Callable<Integer> {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
    private boolean noColor;

    @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
    private boolean json;

    private final FundService fundService;
    private final EstimateAnalysisService estimateAnalysisService;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }

    /**
     * 基金搜索命令
     */
    @Component
    @CommandLine.Command(name = "search", description = "搜索基金")
    @RequiredArgsConstructor
    public static class SearchCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<keyword>", description = "搜索关键词")
        private String keyword;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final FundService fundService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            if (keyword == null || keyword.trim().length() < 2) {
                System.err.println(formatter.formatError("搜索关键词至少需要 2 个字符"));
                return 1;
            }

            List<FundSearchDTO> results = fundService.search(keyword.trim());
            if (results.isEmpty()) {
                System.out.println("未找到匹配的基金");
                return 0;
            }

            System.out.println(formatter.formatFundSearch(results));
            return 0;
        }
    }

    /**
     * 基金详情命令
     */
    @Component
    @CommandLine.Command(name = "detail", description = "查看基金详情")
    @RequiredArgsConstructor
    public static class DetailCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<code>", description = "基金代码")
        private String code;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final FundService fundService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            FundDetailDTO detail = fundService.getDetail(code);
            if (detail == null) {
                System.err.println(formatter.formatError("基金不存在: " + code));
                return 1;
            }

            System.out.println(formatter.formatFundDetail(detail));
            return 0;
        }
    }

    /**
     * 净值历史命令
     */
    @Component
    @CommandLine.Command(name = "nav", description = "查看净值历史")
    @RequiredArgsConstructor
    public static class NavHistoryCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<code>", description = "基金代码")
        private String code;

        @CommandLine.Option(names = {"-p", "--period"}, defaultValue = "3m",
                description = "时间周期: 1m, 3m, 6m, 1y, 3y, all")
        private String period;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final FundService fundService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            NavHistoryDTO history = fundService.getNavHistory(code, period);
            System.out.println(formatter.formatNavHistory(history));
            return 0;
        }
    }

    /**
     * 实时估值命令
     */
    @Component
    @CommandLine.Command(name = "estimate", description = "查看实时估值")
    @RequiredArgsConstructor
    public static class EstimateCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<code>", description = "基金代码")
        private String code;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final FundService fundService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            EstimateSourceDTO estimates = fundService.getMultiSourceEstimates(code);
            System.out.println(formatter.formatEstimates(estimates));
            return 0;
        }
    }

    /**
     * 刷新数据命令
     */
    @Component
    @CommandLine.Command(name = "refresh", description = "刷新基金数据")
    @RequiredArgsConstructor
    public static class RefreshCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<code>", description = "基金代码")
        private String code;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final FundService fundService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            RefreshResultDTO result = fundService.refreshFundData(code);
            if (result == null || result.getDetail() == null) {
                System.err.println(formatter.formatError("基金不存在: " + code));
                return 1;
            }

            System.out.println(formatter.formatSuccess("数据刷新成功"));
            System.out.println(formatter.formatFundDetail(result.getDetail()));
            return 0;
        }
    }

    /**
     * 数据源分析命令
     */
    @Component
    @CommandLine.Command(name = "analysis", description = "查看数据源准确度分析")
    @RequiredArgsConstructor
    public static class AnalysisCommand implements Callable<Integer> {

        @CommandLine.Parameters(paramLabel = "<code>", description = "基金代码")
        private String code;

        @CommandLine.Option(names = {"--no-color"}, description = "禁用颜色输出")
        private boolean noColor;

        @CommandLine.Option(names = {"--json"}, description = "以 JSON 格式输出")
        private boolean json;

        private final EstimateAnalysisService estimateAnalysisService;

        @Override
        public Integer call() {
            CliTableFormatter formatter = new CliTableFormatter(!noColor, json);

            EstimateAnalysisDTO analysis = estimateAnalysisService.getEstimateAnalysis(code);
            System.out.println(formatter.formatEstimateAnalysis(analysis));
            return 0;
        }
    }
}
