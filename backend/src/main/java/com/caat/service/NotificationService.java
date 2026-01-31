package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.Notification;
import com.caat.entity.NotificationRule;
import com.caat.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 通知服务
 * 支持邮件、Webhook、桌面通知（浏览器）
 */
@Slf4j
@Service
public class NotificationService {
    
    private final NotificationRuleService notificationRuleService;
    private final NotificationRepository notificationRepository;
    private final RestTemplate restTemplate;
    private final JavaMailSender mailSender; // 可选，如果未配置则使用日志记录
    
    public NotificationService(
            NotificationRuleService notificationRuleService,
            NotificationRepository notificationRepository,
            RestTemplate restTemplate,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JavaMailSender mailSender) {
        this.notificationRuleService = notificationRuleService;
        this.notificationRepository = notificationRepository;
        this.restTemplate = restTemplate;
        this.mailSender = mailSender;
    }
    
    /**
     * 检查内容是否匹配通知规则并发送通知
     */
    public void checkAndNotify(Content content) {
        List<NotificationRule> enabledRules = notificationRuleService.findAllEnabled();
        
        for (NotificationRule rule : enabledRules) {
            if (matchesRule(content, rule) && !isInQuietHours(rule)) {
                sendNotification(content, rule);
            }
        }
    }
    
    /**
     * 检查是否在免打扰时段
     */
    private boolean isInQuietHours(NotificationRule rule) {
        Map<String, Object> quietHours = rule.getQuietHours();
        if (quietHours == null) {
            return false;
        }
        
        Boolean enabled = (Boolean) quietHours.get("enabled");
        if (enabled == null || !enabled) {
            return false;
        }
        
        String startStr = (String) quietHours.get("start");
        String endStr = (String) quietHours.get("end");
        if (startStr == null || endStr == null) {
            return false;
        }
        
        try {
            LocalTime start = LocalTime.parse(startStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime end = LocalTime.parse(endStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalTime now = LocalTime.now();
            
            // 处理跨天的情况（例如 22:00 - 08:00）
            if (start.isAfter(end)) {
                return now.isAfter(start) || now.isBefore(end);
            } else {
                return now.isAfter(start) && now.isBefore(end);
            }
        } catch (Exception e) {
            log.warn("解析免打扰时段失败: rule={}", rule.getName(), e);
            return false;
        }
    }
    
    /**
     * 检查内容是否匹配规则
     */
    private boolean matchesRule(Content content, NotificationRule rule) {
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return false;
        }
        
        String ruleType = rule.getRuleType();
        if (ruleType == null) {
            return false;
        }
        
        return switch (ruleType.toUpperCase()) {
            case "KEYWORD" -> matchesKeyword(content, config);
            case "AUTHOR" -> matchesAuthor(content, config);
            case "PLATFORM" -> matchesPlatform(content, config);
            default -> false;
        };
    }
    
    /**
     * 关键词匹配
     */
    private boolean matchesKeyword(Content content, Map<String, Object> config) {
        String keyword = (String) config.get("keyword");
        if (keyword == null || keyword.isEmpty()) {
            return false;
        }
        
        String title = content.getTitle() != null ? content.getTitle().toLowerCase() : "";
        String body = content.getBody() != null ? content.getBody().toLowerCase() : "";
        String lowerKeyword = keyword.toLowerCase();
        
        return title.contains(lowerKeyword) || body.contains(lowerKeyword);
    }
    
    /**
     * 作者匹配
     */
    private boolean matchesAuthor(Content content, Map<String, Object> config) {
        String authorId = (String) config.get("authorId");
        if (authorId == null) {
            return false;
        }
        
        return content.getUser().getId().toString().equals(authorId);
    }
    
    /**
     * 平台匹配
     */
    private boolean matchesPlatform(Content content, Map<String, Object> config) {
        String platformId = (String) config.get("platformId");
        if (platformId == null) {
            return false;
        }
        
        return content.getPlatform().getId().toString().equals(platformId);
    }
    
    /**
     * 发送通知
     */
    private void sendNotification(Content content, NotificationRule rule) {
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return;
        }
        
        @SuppressWarnings("unchecked")
        List<String> methods = (List<String>) config.get("notificationMethods");
        if (methods == null || methods.isEmpty()) {
            return;
        }
        
        for (String method : methods) {
            try {
                switch (method.toUpperCase()) {
                    case "EMAIL" -> sendEmailNotification(content, rule, config);
                    case "WEBHOOK" -> sendWebhookNotification(content, rule, config);
                    case "DESKTOP" -> sendDesktopNotification(content, rule); // 桌面通知需要前端配合
                    default -> log.warn("未知的通知方式: {}", method);
                }
            } catch (Exception e) {
                log.error("发送通知失败: method={}, rule={}", method, rule.getName(), e);
            }
        }
    }
    
    /**
     * 发送邮件通知
     */
    private void sendEmailNotification(Content content, NotificationRule rule, Map<String, Object> config) {
        String email = (String) config.get("email");
        if (email == null || email.isEmpty()) {
            log.warn("邮件地址未配置: rule={}", rule.getName());
            return;
        }
        
        if (mailSender == null) {
            log.warn("邮件服务未配置，跳过邮件通知: rule={}", rule.getName());
            return;
        }
        
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("内容通知: " + (content.getTitle() != null ? content.getTitle() : "新内容"));
            message.setText(buildEmailContent(content, rule));
            
            mailSender.send(message);
            log.info("邮件通知已发送: email={}, contentId={}", email, content.getId());
            
            // 保存通知记录
            saveNotificationRecord(content, rule, "EMAIL");
        } catch (Exception e) {
            log.error("发送邮件通知失败: email={}", email, e);
        }
    }
    
    /**
     * 发送 Webhook 通知
     */
    private void sendWebhookNotification(Content content, NotificationRule rule, Map<String, Object> config) {
        String webhookUrl = (String) config.get("webhookUrl");
        if (webhookUrl == null || webhookUrl.isEmpty()) {
            log.warn("Webhook URL 未配置: rule={}", rule.getName());
            return;
        }
        
        try {
            Map<String, Object> payload = Map.of(
                "rule", rule.getName(),
                "contentId", content.getId().toString(),
                "title", content.getTitle() != null ? content.getTitle() : "",
                "url", content.getUrl(),
                "author", content.getUser().getUsername(),
                "platform", content.getPlatform().getName(),
                "publishedAt", content.getPublishedAt().toString()
            );
            
            restTemplate.postForObject(webhookUrl, payload, String.class);
            log.info("Webhook 通知已发送: url={}, contentId={}", webhookUrl, content.getId());
            
            // 保存通知记录
            saveNotificationRecord(content, rule, "WEBHOOK");
        } catch (Exception e) {
            log.error("发送 Webhook 通知失败: url={}", webhookUrl, e);
        }
    }
    
    /**
     * 发送桌面通知（需要前端配合，这里保存通知记录）
     */
    @Transactional
    private void sendDesktopNotification(Content content, NotificationRule rule) {
        // 保存通知记录，前端可以轮询获取并显示
        Notification notification = new Notification();
        notification.setRuleId(rule.getId());
        notification.setContentId(content.getId());
        notification.setTitle("新内容: " + (content.getTitle() != null ? content.getTitle() : "无标题"));
        notification.setMessage(buildNotificationMessage(content, rule));
        notification.setNotificationType("DESKTOP");
        notification.setIsRead(false);
        notificationRepository.save(notification);
        log.info("桌面通知记录已保存: rule={}, contentId={}", rule.getName(), content.getId());
    }
    
    /**
     * 保存通知记录（用于所有通知类型）
     */
    @Transactional
    private void saveNotificationRecord(Content content, NotificationRule rule, String notificationType) {
        Notification notification = new Notification();
        notification.setRuleId(rule.getId());
        notification.setContentId(content.getId());
        notification.setTitle("新内容: " + (content.getTitle() != null ? content.getTitle() : "无标题"));
        notification.setMessage(buildNotificationMessage(content, rule));
        notification.setNotificationType(notificationType);
        notification.setIsRead(false);
        notificationRepository.save(notification);
    }
    
    /**
     * 构建通知消息
     */
    private String buildNotificationMessage(Content content, NotificationRule rule) {
        return String.format("规则: %s\n作者: %s\n平台: %s\n链接: %s", 
                rule.getName(),
                content.getUser().getUsername(),
                content.getPlatform().getName(),
                content.getUrl());
    }
    
    /**
     * 构建邮件内容
     */
    private String buildEmailContent(Content content, NotificationRule rule) {
        StringBuilder sb = new StringBuilder();
        sb.append("通知规则: ").append(rule.getName()).append("\n\n");
        sb.append("标题: ").append(content.getTitle() != null ? content.getTitle() : "无标题").append("\n");
        sb.append("作者: ").append(content.getUser().getUsername()).append("\n");
        sb.append("平台: ").append(content.getPlatform().getName()).append("\n");
        sb.append("发布时间: ").append(content.getPublishedAt()).append("\n");
        sb.append("链接: ").append(content.getUrl()).append("\n\n");
        if (content.getBody() != null && !content.getBody().isEmpty()) {
            sb.append("内容预览:\n").append(content.getBody().substring(0, Math.min(200, content.getBody().length()))).append("...\n");
        }
        return sb.toString();
    }
}
