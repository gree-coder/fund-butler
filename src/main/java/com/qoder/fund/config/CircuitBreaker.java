package com.qoder.fund.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 简易熔断器
 * 用于外部 API 调用的容错保护
 */
@Slf4j
@Component
public class CircuitBreaker {

    /**
     * 熔断器状态
     */
    public enum State {
        CLOSED,      // 关闭（正常）
        OPEN,        // 打开（熔断）
        HALF_OPEN    // 半开（尝试恢复）
    }

    /**
     * 熔断器配置
     */
    public static class Config {
        private int failureThreshold = 5;           // 失败阈值
        private Duration openDuration = Duration.ofSeconds(30);  // 熔断持续时间
        private int halfOpenMaxCalls = 3;           // 半开状态最大尝试次数

        public Config failureThreshold(int threshold) {
            this.failureThreshold = threshold;
            return this;
        }

        public Config openDuration(Duration duration) {
            this.openDuration = duration;
            return this;
        }

        public Config halfOpenMaxCalls(int maxCalls) {
            this.halfOpenMaxCalls = maxCalls;
            return this;
        }
    }

    /**
     * 熔断器实例
     */
    private static class CircuitInstance {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
        private final Config config;

        CircuitInstance(Config config) {
            this.config = config;
        }

        int getFailureCount() {
            return failureCount.get();
        }

        int incrementFailure() {
            return failureCount.incrementAndGet();
        }

        void resetFailureCount() {
            failureCount.set(0);
        }

        int getSuccessCount() {
            return successCount.get();
        }

        int incrementSuccess() {
            return successCount.incrementAndGet();
        }

        void resetSuccessCount() {
            successCount.set(0);
        }

        State getState() {
            return state.get();
        }

        boolean compareAndSetState(State expect, State update) {
            return state.compareAndSet(expect, update);
        }

        Instant getLastFailureTime() {
            return lastFailureTime.get();
        }

        void setLastFailureTime(Instant time) {
            lastFailureTime.set(time);
        }

        Config getConfig() {
            return config;
        }
    }

    private final Map<String, CircuitInstance> circuits = new ConcurrentHashMap<>();

    // 默认配置
    private final Config defaultConfig = new Config();

    // 不同数据源的配置
    private final Map<String, Config> sourceConfigs = Map.of(
            "eastmoney", new Config().failureThreshold(3).openDuration(Duration.ofSeconds(60)),
            "sina", new Config().failureThreshold(5).openDuration(Duration.ofSeconds(30)),
            "tencent", new Config().failureThreshold(5).openDuration(Duration.ofSeconds(30)),
            "stock", new Config().failureThreshold(10).openDuration(Duration.ofSeconds(20))
    );

    /**
     * 检查是否允许调用
     */
    public boolean allowRequest(String sourceName) {
        CircuitInstance circuit = circuits.computeIfAbsent(sourceName,
                k -> new CircuitInstance(getConfig(sourceName)));

        State currentState = circuit.getState();

        switch (currentState) {
            case CLOSED:
                return true;

            case OPEN:
                // 检查是否可以进入半开状态
                Instant lastFailure = circuit.getLastFailureTime();
                if (lastFailure != null
                        && Duration.between(lastFailure, Instant.now())
                                .compareTo(circuit.getConfig().openDuration) > 0) {
                    circuit.compareAndSetState(State.OPEN, State.HALF_OPEN);
                    circuit.resetSuccessCount();
                    log.info("熔断器 {} 进入半开状态", sourceName);
                    return true;
                }
                return false;

            case HALF_OPEN:
                // 半开状态限制请求数
                return circuit.getSuccessCount() < circuit.getConfig().halfOpenMaxCalls;

            default:
                return true;
        }
    }

    /**
     * 记录成功
     */
    public void recordSuccess(String sourceName) {
        CircuitInstance circuit = circuits.get(sourceName);
        if (circuit == null) {
            return;
        }

        circuit.resetFailureCount();

        if (circuit.getState() == State.HALF_OPEN) {
            int successCount = circuit.incrementSuccess();
            if (successCount >= circuit.getConfig().halfOpenMaxCalls) {
                circuit.compareAndSetState(State.HALF_OPEN, State.CLOSED);
                log.info("熔断器 {} 恢复正常", sourceName);
            }
        }
    }

    /**
     * 记录失败
     */
    public void recordFailure(String sourceName) {
        CircuitInstance circuit = circuits.computeIfAbsent(sourceName,
                k -> new CircuitInstance(getConfig(sourceName)));

        circuit.setLastFailureTime(Instant.now());
        int failures = circuit.incrementFailure();

        if (circuit.getState() == State.HALF_OPEN) {
            // 半开状态下失败，立即熔断
            circuit.compareAndSetState(State.HALF_OPEN, State.OPEN);
            log.warn("熔断器 {} 在半开状态失败，重新熔断", sourceName);
        } else if (failures >= circuit.getConfig().failureThreshold) {
            // 失败次数达到阈值，进入熔断
            if (circuit.compareAndSetState(State.CLOSED, State.OPEN)) {
                log.warn("熔断器 {} 触发熔断，失败次数: {}", sourceName, failures);
            }
        }
    }

    /**
     * 获取熔断器状态
     */
    public State getState(String sourceName) {
        CircuitInstance circuit = circuits.get(sourceName);
        return circuit != null ? circuit.getState() : State.CLOSED;
    }

    /**
     * 重置熔断器
     */
    public void reset(String sourceName) {
        circuits.remove(sourceName);
        log.info("熔断器 {} 已重置", sourceName);
    }

    private Config getConfig(String sourceName) {
        return sourceConfigs.getOrDefault(sourceName, defaultConfig);
    }
}
