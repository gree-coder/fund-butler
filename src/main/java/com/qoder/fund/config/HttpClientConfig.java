package com.qoder.fund.config;

import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * HTTP 客户端配置
 * 共享 OkHttpClient 连接池，减少资源消耗
 */
@Configuration
public class HttpClientConfig {

    /**
     * 共享的 OkHttpClient 实例
     * 使用连接池复用连接，提高性能
     */
    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();
    }
}
