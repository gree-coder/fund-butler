package com.qoder.fund.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 分层缓存配置
 * 根据数据冷热程度设置不同的缓存策略
 */
@Configuration
@EnableCaching
public class CacheConfig {

    // ========== 热数据缓存（高频访问，短过期时间）==========
    private static final int HOT_EXPIRE_SECONDS = 60;      // 1分钟
    private static final int HOT_MAX_SIZE = 500;

    // ========== 温数据缓存（中频访问，中等过期时间）==========
    private static final int WARM_EXPIRE_SECONDS = 300;    // 5分钟
    private static final int WARM_MAX_SIZE = 2000;

    // ========== 冷数据缓存（低频访问，长过期时间）==========
    private static final int COLD_EXPIRE_SECONDS = 3600;   // 1小时
    private static final int COLD_MAX_SIZE = 1000;

    // ========== 持久数据缓存（极少变化）==========
    private static final int PERSISTENT_EXPIRE_SECONDS = 86400;  // 24小时
    private static final int PERSISTENT_MAX_SIZE = 500;

    private static final int RANDOM_EXPIRE_RANGE = 30;  // 随机偏移范围

    /**
     * 默认缓存管理器（温数据）
     */
    @Primary
    @Bean
    public CacheManager cacheManager() {
        return createCacheManager(WARM_EXPIRE_SECONDS, WARM_MAX_SIZE,
                "fundSearch", "fundDetail", "navHistory", "estimateNav",
                "dashboard", "profitTrend", "profitAnalysis");
    }

    /**
     * 热数据缓存管理器（用户持仓、自选基金实时估值、AI分析）
     */
    @Bean("hotCacheManager")
    public CacheManager hotCacheManager() {
        return createCacheManager(HOT_EXPIRE_SECONDS, HOT_MAX_SIZE,
                "positionEstimate", "watchlistEstimate", "multiSourceEstimates");
    }

    /**
     * AI分析缓存管理器（调仓建议等，15分钟过期）
     */
    @Bean("aiCacheManager")
    public CacheManager aiCacheManager() {
        return createCacheManager(900, 200,
                "aiFundDiagnosis", "rebalanceTiming", "positionRiskWarning");
    }

    /**
     * 市场数据缓存管理器（5分钟过期）
     */
    @Bean("marketCacheManager")
    public CacheManager marketCacheManager() {
        return createCacheManager(300, 100,
                "marketOverview");
    }

    /**
     * 冷数据缓存管理器（搜索历史、不常用基金）
     */
    @Bean("coldCacheManager")
    public CacheManager coldCacheManager() {
        return createCacheManager(COLD_EXPIRE_SECONDS, COLD_MAX_SIZE,
                "fundSearchHistory", "allHoldings");
    }

    /**
     * 持久数据缓存管理器（基金基本信息）
     */
    @Bean("persistentCacheManager")
    public CacheManager persistentCacheManager() {
        return createCacheManager(PERSISTENT_EXPIRE_SECONDS, PERSISTENT_MAX_SIZE,
                "fundBasicInfo");
    }

    private CaffeineCacheManager createCacheManager(int baseExpireSeconds, int maxSize, String... cacheNames) {
        CaffeineCacheManager manager = new CaffeineCacheManager(cacheNames);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(getRandomExpireSeconds(baseExpireSeconds), TimeUnit.SECONDS)
                .recordStats()
                // 启用基于引用的清理（软引用值）
                .softValues());
        return manager;
    }

    private int getRandomExpireSeconds(int baseSeconds) {
        int randomOffset = ThreadLocalRandom.current().nextInt(RANDOM_EXPIRE_RANGE);
        return baseSeconds + randomOffset;
    }
}
