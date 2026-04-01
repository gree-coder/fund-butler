package com.qoder.fund.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 基础缓存过期时间（秒）
     */
    private static final int BASE_EXPIRE_SECONDS = 300;  // 5分钟

    /**
     * 随机过期时间范围（秒），用于防止缓存雪崩
     */
    private static final int RANDOM_EXPIRE_RANGE = 60;  // 0-60秒随机偏移

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(2000)
                // 使用随机过期时间防止雪崩
                .expireAfterWrite(getRandomExpireSeconds(), TimeUnit.SECONDS)
                // 记录缓存统计
                .recordStats());
        return manager;
    }

    /**
     * 获取随机过期时间（基础时间 + 随机偏移）
     * 防止大量缓存同时失效导致雪崩
     */
    private int getRandomExpireSeconds() {
        int randomOffset = ThreadLocalRandom.current().nextInt(RANDOM_EXPIRE_RANGE);
        return BASE_EXPIRE_SECONDS + randomOffset;
    }
}
