package com.caat.config;

import com.google.common.util.concurrent.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * API 访问频率限制配置
 */
@Configuration
public class RateLimitConfig {
    
    /**
     * 全局速率限制器（每秒允许的请求数）
     */
    @Bean
    public RateLimiter globalRateLimiter() {
        return RateLimiter.create(100.0); // 每秒 100 个请求
    }
    
    /**
     * 用户速率限制器映射（每个用户独立的限制器）
     */
    @Bean
    public ConcurrentHashMap<String, RateLimiter> userRateLimiters() {
        return new ConcurrentHashMap<>();
    }
    
    /**
     * 获取或创建用户速率限制器
     */
    public RateLimiter getUserRateLimiter(String userId) {
        return userRateLimiters().computeIfAbsent(userId, 
            k -> RateLimiter.create(10.0)); // 每个用户每秒 10 个请求
    }
}
