package com.caat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安全审计服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    
    // 安全事件日志（实际应该存储到数据库）
    private final Map<String, SecurityEvent> securityEvents = new ConcurrentHashMap<>();
    
    /**
     * 记录安全事件
     */
    public void logSecurityEvent(String eventType, String userId, String ipAddress, String details) {
        SecurityEvent event = new SecurityEvent();
        event.setEventType(eventType);
        event.setUserId(userId);
        event.setIpAddress(ipAddress);
        event.setDetails(details);
        event.setTimestamp(LocalDateTime.now());
        
        securityEvents.put(event.getId(), event);
        
        // 记录到日志
        log.warn("安全事件: type={}, userId={}, ip={}, details={}", 
                eventType, userId, ipAddress, details);
    }
    
    /**
     * 记录登录失败事件
     */
    public void logLoginFailure(String username, String ipAddress) {
        logSecurityEvent("LOGIN_FAILURE", username, ipAddress, "登录失败");
    }
    
    /**
     * 记录登录成功事件
     */
    public void logLoginSuccess(String username, String ipAddress) {
        logSecurityEvent("LOGIN_SUCCESS", username, ipAddress, "登录成功");
    }
    
    /**
     * 记录权限拒绝事件
     */
    public void logAccessDenied(String userId, String resource, String ipAddress) {
        logSecurityEvent("ACCESS_DENIED", userId, ipAddress, 
                "访问被拒绝: " + resource);
    }
    
    /**
     * 记录异常访问事件
     */
    public void logSuspiciousActivity(String userId, String ipAddress, String activity) {
        logSecurityEvent("SUSPICIOUS_ACTIVITY", userId, ipAddress, activity);
    }
    
    /**
     * 获取安全事件统计
     */
    public Map<String, Long> getSecurityEventStatistics(int hours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(hours);
        Map<String, Long> stats = new HashMap<>();
        
        securityEvents.values().stream()
                .filter(event -> event.getTimestamp().isAfter(cutoff))
                .forEach(event -> {
                    stats.merge(event.getEventType(), 1L, Long::sum);
                });
        
        return stats;
    }
    
    /**
     * 安全事件实体
     */
    public static class SecurityEvent {
        private String id = java.util.UUID.randomUUID().toString();
        private String eventType;
        private String userId;
        private String ipAddress;
        private String details;
        private LocalDateTime timestamp;
        
        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    }
}
