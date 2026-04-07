package com.qoder.fund.service;

import com.qoder.fund.dto.EstimateSourceDTO;
import com.qoder.fund.datasource.FundDataAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

/**
 * 批量估值服务
 * 优化持仓列表等场景下的多基金估值查询性能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchEstimateService {

    private final FundDataAggregator dataAggregator;

    // 批量查询线程池
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors() * 2,
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "batch-estimate-" + (++count));
                }
            }
    );

    /**
     * 批量获取基金最新净值
     *
     * @param fundCodes 基金代码集合
     * @return 基金代码 -> 最新净值的映射
     */
    public Map<String, BigDecimal> batchGetLatestNav(Set<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, BigDecimal> result = new ConcurrentHashMap<>();

        // 分批处理，每批10个
        List<List<String>> batches = partitionList(new ArrayList<>(fundCodes), 10);

        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    for (String fundCode : batch) {
                        try {
                            BigDecimal nav = dataAggregator.getLatestNav(fundCode);
                            if (nav != null) {
                                result.put(fundCode, nav);
                            }
                        } catch (Exception e) {
                            log.warn("批量获取净值失败: {}", fundCode, e);
                        }
                    }
                }, batchExecutor))
                .toList();

        // 等待所有批次完成，设置超时
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("批量获取净值超时或异常", e);
        }

        return result;
    }

    /**
     * 批量获取基金估值
     *
     * @param fundCodes 基金代码集合
     * @return 基金代码 -> 估值信息的映射
     */
    public Map<String, Map<String, Object>> batchGetEstimates(Set<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, Object>> result = new ConcurrentHashMap<>();

        // 分批处理，每批3个（估值接口较慢，减少批次大小，避免触发限流）
        List<List<String>> batches = partitionList(new ArrayList<>(fundCodes), 3);

        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    for (String fundCode : batch) {
                        try {
                            Map<String, Object> estimate = dataAggregator.getEstimateNav(fundCode);
                            if (estimate != null && !estimate.isEmpty()) {
                                result.put(fundCode, estimate);
                            }
                        } catch (Exception e) {
                            log.warn("批量获取估值失败: {}", fundCode, e);
                        }
                        // 添加延迟，避免触发限流（每只基金间隔100ms）
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }, batchExecutor))
                .toList();

        // 等待所有批次完成，设置超时
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("批量获取估值超时或异常", e);
        }

        return result;
    }

    /**
     * 批量获取多源估值（用于持仓列表展示）
     *
     * @param fundCodes 基金代码集合
     * @return 基金代码 -> 多源估值的映射
     */
    public Map<String, EstimateSourceDTO> batchGetMultiSourceEstimates(Set<String> fundCodes) {
        if (fundCodes == null || fundCodes.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, EstimateSourceDTO> result = new ConcurrentHashMap<>();

        // 分批处理，每批3个（多源估值较慢，减少批次大小，避免触发限流）
        List<List<String>> batches = partitionList(new ArrayList<>(fundCodes), 3);

        List<CompletableFuture<Void>> futures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    for (String fundCode : batch) {
                        try {
                            EstimateSourceDTO estimates = dataAggregator.getMultiSourceEstimates(fundCode);
                            if (estimates != null && estimates.getSources() != null) {
                                result.put(fundCode, estimates);
                            }
                        } catch (Exception e) {
                            log.warn("批量获取多源估值失败: {}", fundCode, e);
                        }
                        // 添加延迟，避免触发限流（每只基金间隔150ms）
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }, batchExecutor))
                .toList();

        // 等待所有批次完成，设置超时
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("批量获取多源估值超时或异常", e);
        }

        return result;
    }

    /**
     * 综合批量查询（净值 + 估值）- 用于持仓列表
     *
     * @param fundCodes 基金代码集合
     * @return 批量估值结果
     */
    public BatchEstimateResult batchGetPositionEstimates(Set<String> fundCodes) {
        long startTime = System.currentTimeMillis();

        // 并行获取净值和估值
        CompletableFuture<Map<String, BigDecimal>> navFuture = CompletableFuture
                .supplyAsync(() -> batchGetLatestNav(fundCodes), batchExecutor);

        CompletableFuture<Map<String, Map<String, Object>>> estimateFuture = CompletableFuture
                .supplyAsync(() -> batchGetEstimates(fundCodes), batchExecutor);

        CompletableFuture<Map<String, EstimateSourceDTO>> multiSourceFuture = CompletableFuture
                .supplyAsync(() -> batchGetMultiSourceEstimates(fundCodes), batchExecutor);

        // 等待所有查询完成
        try {
            CompletableFuture.allOf(navFuture, estimateFuture, multiSourceFuture).join();

            Map<String, BigDecimal> navMap = navFuture.get();
            Map<String, Map<String, Object>> estimateMap = estimateFuture.get();
            Map<String, EstimateSourceDTO> multiSourceMap = multiSourceFuture.get();

            long duration = System.currentTimeMillis() - startTime;
            log.info("批量估值完成: {} 只基金, 耗时 {}ms", fundCodes.size(), duration);

            return new BatchEstimateResult(navMap, estimateMap, multiSourceMap);
        } catch (Exception e) {
            log.error("批量估值失败", e);
            return new BatchEstimateResult(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyMap()
            );
        }
    }

    /**
     * 将列表分区
     */
    private <T> List<List<T>> partitionList(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * 批量估值结果
     */
    public record BatchEstimateResult(
            Map<String, BigDecimal> latestNavMap,
            Map<String, Map<String, Object>> estimateMap,
            Map<String, EstimateSourceDTO> multiSourceEstimateMap
    ) {
        /**
         * 获取指定基金的智能估值
         */
        public BigDecimal getSmartEstimateReturn(String fundCode) {
            EstimateSourceDTO dto = multiSourceEstimateMap.get(fundCode);
            if (dto == null || dto.getSources() == null) {
                return null;
            }
            return dto.getSources().stream()
                    .filter(s -> "smart".equals(s.getKey()) && s.isAvailable())
                    .findFirst()
                    .map(EstimateSourceDTO.EstimateItem::getEstimateReturn)
                    .orElse(null);
        }

        /**
         * 获取指定基金的实际净值
         */
        public BigDecimal getActualReturn(String fundCode) {
            EstimateSourceDTO dto = multiSourceEstimateMap.get(fundCode);
            if (dto == null || dto.getSources() == null) {
                return null;
            }
            return dto.getSources().stream()
                    .filter(s -> "actual".equals(s.getKey()) && s.isAvailable())
                    .findFirst()
                    .map(EstimateSourceDTO.EstimateItem::getEstimateReturn)
                    .orElse(null);
        }
    }
}
