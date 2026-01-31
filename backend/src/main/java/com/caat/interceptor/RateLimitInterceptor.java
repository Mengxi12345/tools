package com.caat.interceptor;

import com.caat.config.RateLimitConfig;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API 访问频率限制拦截器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final RateLimitConfig rateLimitConfig;
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 获取用户 ID（从请求头或 Token 中）
        String userId = request.getHeader("X-User-Id");
        
        RateLimiter rateLimiter;
        if (userId != null && !userId.isEmpty()) {
            // 使用用户级别的速率限制
            rateLimiter = rateLimitConfig.getUserRateLimiter(userId);
        } else {
            // 使用全局速率限制
            rateLimiter = rateLimitConfig.globalRateLimiter();
        }
        
        // 尝试获取许可
        if (!rateLimiter.tryAcquire()) {
            log.warn("API 访问频率超限: userId={}, path={}", userId, request.getRequestURI());
            response.setStatus(429); // Too Many Requests
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }
        
        return true;
    }
}
