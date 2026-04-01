package com.qoder.fund.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/**
 * 健康检查配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HealthCheckConfig {

    private final DataSource dataSource;
    private final CircuitBreaker circuitBreaker;

    /**
     * 数据库健康检查
     */
    @Bean
    public HealthIndicator databaseHealthIndicator() {
        return () -> {
            try (Connection connection = dataSource.getConnection()) {
                if (connection.isValid(5)) {
                    return Health.up()
                            .withDetail("database", "MySQL")
                            .withDetail("status", "Connected")
                            .build();
                }
                return Health.down()
                        .withDetail("database", "MySQL")
                        .withDetail("error", "Connection invalid")
                        .build();
            } catch (Exception e) {
                log.error("数据库健康检查失败", e);
                return Health.down()
                        .withDetail("database", "MySQL")
                        .withDetail("error", e.getMessage())
                        .build();
            }
        };
    }

    /**
     * 数据源健康检查
     */
    @Bean
    public HealthIndicator dataSourceHealthIndicator() {
        return () -> {
            Health.Builder builder = Health.up()
                    .withDetail("description", "External data sources status");

            Map<String, CircuitBreaker.State> states = Map.of(
                    "eastmoney", circuitBreaker.getState("eastmoney"),
                    "sina", circuitBreaker.getState("sina"),
                    "tencent", circuitBreaker.getState("tencent"),
                    "stock", circuitBreaker.getState("stock")
            );

            long openCount = states.values().stream()
                    .filter(s -> s == CircuitBreaker.State.OPEN)
                    .count();

            states.forEach((name, state) -> {
                builder.withDetail(name + "_circuit", state.toString());
            });

            if (openCount > 2) {
                builder.down()
                        .withDetail("status", "DEGRADED")
                        .withDetail("message", "Multiple data sources are down");
            } else if (openCount > 0) {
                builder.status("DEGRADED")
                        .withDetail("status", "PARTIAL")
                        .withDetail("message", "Some data sources are down");
            } else {
                builder.withDetail("status", "HEALTHY");
            }

            return builder.build();
        };
    }

    /**
     * 缓存健康检查
     */
    @Bean
    public HealthIndicator cacheHealthIndicator() {
        return () -> {
            // 这里可以添加缓存统计信息
            return Health.up()
                    .withDetail("cache", "Caffeine")
                    .withDetail("status", "Active")
                    .build();
        };
    }
}
