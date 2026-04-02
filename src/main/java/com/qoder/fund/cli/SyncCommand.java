package com.qoder.fund.cli;

import com.qoder.fund.scheduler.FundDataSyncScheduler;
import com.qoder.fund.service.TradingCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine;

import java.time.LocalDate;
import java.util.concurrent.Callable;

/**
 * 数据同步 CLI 命令
 * 
 * 用于手动触发数据同步任务，替代 Web 模式下的定时任务
 * 
 * 使用示例:
 *   java -jar fund-cli.jar sync nav          # 同步净值
 *   java -jar fund-cli.jar sync estimate     # 快照估值
 *   java -jar fund-cli.jar sync all          # 执行全部同步
 */
@Component
@CommandLine.Command(
        name = "sync",
        description = "数据同步命令（用于定时任务或手动同步）",
        subcommands = {
                SyncCommand.NavCommand.class,
                SyncCommand.EstimateCommand.class,
                SyncCommand.HoldingsCommand.class,
                SyncCommand.EvaluateCommand.class,
                SyncCommand.CompensateCommand.class,
                SyncCommand.AllCommand.class
        }
)
@RequiredArgsConstructor
public class SyncCommand implements Callable<Integer> {

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }

    /**
     * 同步净值数据命令
     */
    @Component
    @CommandLine.Command(name = "nav", description = "同步当日净值数据")
    @RequiredArgsConstructor
    public static class NavCommand implements Callable<Integer> {

        private final FundDataSyncScheduler scheduler;
        private final TradingCalendarService tradingCalendarService;

        @Override
        public Integer call() {
            LocalDate today = LocalDate.now();
            
            if (!tradingCalendarService.isTradingDay(today)) {
                System.out.println("今日非交易日，跳过净值同步");
                return 0;
            }

            System.out.println("开始同步净值数据... " + today);
            long start = System.currentTimeMillis();
            
            scheduler.syncDailyNav();
            
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("净值同步完成，耗时 " + elapsed + "ms");
            return 0;
        }
    }

    /**
     * 快照估值预测命令
     */
    @Component
    @CommandLine.Command(name = "estimate", description = "快照基金估值预测")
    @RequiredArgsConstructor
    public static class EstimateCommand implements Callable<Integer> {

        @CommandLine.Option(names = {"--qdii"}, description = "仅处理 QDII 基金")
        private boolean qdiiOnly;

        private final FundDataSyncScheduler scheduler;
        private final TradingCalendarService tradingCalendarService;

        @Override
        public Integer call() {
            LocalDate today = LocalDate.now();

            if (!tradingCalendarService.isTradingDay(today)) {
                System.out.println("今日非交易日，跳过估值快照");
                return 0;
            }

            System.out.println("开始快照估值预测... " + today);
            long start = System.currentTimeMillis();

            if (qdiiOnly) {
                System.out.println("仅处理 QDII 基金");
                scheduler.snapshotQdiiFundPredictions();
            } else {
                // 先快照 A 股基金
                scheduler.snapshotDomesticFundPredictions();
                // 再快照 QDII 基金
                scheduler.snapshotQdiiFundPredictions();
            }

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("估值快照完成，耗时 " + elapsed + "ms");
            return 0;
        }
    }

    /**
     * 同步重仓股数据命令
     */
    @Component
    @CommandLine.Command(name = "holdings", description = "同步基金重仓股数据")
    @RequiredArgsConstructor
    public static class HoldingsCommand implements Callable<Integer> {

        private final FundDataSyncScheduler scheduler;

        @Override
        public Integer call() {
            System.out.println("开始同步重仓股数据...");
            long start = System.currentTimeMillis();

            scheduler.syncHoldings();

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("重仓股同步完成，耗时 " + elapsed + "ms");
            return 0;
        }
    }

    /**
     * 评估预测准确度命令
     */
    @Component
    @CommandLine.Command(name = "evaluate", description = "评估预测准确度")
    @RequiredArgsConstructor
    public static class EvaluateCommand implements Callable<Integer> {

        private final FundDataSyncScheduler scheduler;
        private final TradingCalendarService tradingCalendarService;

        @Override
        public Integer call() {
            LocalDate today = LocalDate.now();

            if (!tradingCalendarService.isTradingDay(today)) {
                System.out.println("今日非交易日，跳过预测评估");
                return 0;
            }

            System.out.println("开始评估预测准确度... " + today);
            long start = System.currentTimeMillis();

            // 执行三个批次的评估
            scheduler.evaluatePredictionAccuracyBatch1();
            scheduler.evaluatePredictionAccuracyBatch2();
            scheduler.evaluatePredictionAccuracyBatch3();

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("预测评估完成，耗时 " + elapsed + "ms");
            return 0;
        }
    }

    /**
     * 补偿缺失数据命令
     */
    @Component
    @CommandLine.Command(name = "compensate", description = "补偿缺失的历史数据")
    @RequiredArgsConstructor
    public static class CompensateCommand implements Callable<Integer> {

        private final FundDataSyncScheduler scheduler;

        @Override
        public Integer call() {
            System.out.println("开始补偿缺失数据...");
            long start = System.currentTimeMillis();

            scheduler.compensateOnStartup();

            long elapsed = System.currentTimeMillis() - start;
            System.out.println("数据补偿完成，耗时 " + elapsed + "ms");
            return 0;
        }
    }

    /**
     * 执行全部同步命令
     */
    @Component
    @CommandLine.Command(name = "all", description = "执行全部数据同步任务")
    @RequiredArgsConstructor
    public static class AllCommand implements Callable<Integer> {

        private final FundDataSyncScheduler scheduler;
        private final TradingCalendarService tradingCalendarService;

        @Override
        public Integer call() {
            LocalDate today = LocalDate.now();
            boolean isTradingDay = tradingCalendarService.isTradingDay(today);

            System.out.println("====================================");
            System.out.println("开始执行全部数据同步任务");
            System.out.println("日期: " + today + (isTradingDay ? " (交易日)" : " (非交易日)"));
            System.out.println("====================================");

            long totalStart = System.currentTimeMillis();

            // 1. 先补偿缺失数据
            System.out.println("\n[1/5] 补偿缺失数据...");
            scheduler.compensateOnStartup();

            if (isTradingDay) {
                // 2. 同步净值
                System.out.println("\n[2/5] 同步净值数据...");
                scheduler.syncDailyNav();

                // 3. 快照估值
                System.out.println("\n[3/5] 快照估值预测...");
                scheduler.snapshotDomesticFundPredictions();
                scheduler.snapshotQdiiFundPredictions();

                // 4. 评估预测
                System.out.println("\n[4/5] 评估预测准确度...");
                scheduler.evaluatePredictionAccuracyBatch1();
                scheduler.evaluatePredictionAccuracyBatch2();
                scheduler.evaluatePredictionAccuracyBatch3();
            } else {
                System.out.println("\n[2/5-4/5] 非交易日，跳过净值同步和估值相关任务");
            }

            // 5. 同步重仓股
            System.out.println("\n[5/5] 同步重仓股数据...");
            scheduler.syncHoldings();

            long totalElapsed = System.currentTimeMillis() - totalStart;
            System.out.println("\n====================================");
            System.out.println("全部同步任务完成，总耗时 " + totalElapsed + "ms");
            System.out.println("====================================");

            return 0;
        }
    }
}
