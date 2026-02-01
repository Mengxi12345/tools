package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.Notification;
import com.caat.entity.NotificationRule;
import com.caat.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
    private final NotificationChannelConfigService channelConfigService; // 可选，用于解析 channelConfigId

    @Value("${app.qq.default-api-url:}")
    private String defaultQqApiUrl;

    @Value("${app.feishu.default-app-id:}")
    private String defaultFeishuAppId;

    @Value("${app.feishu.default-app-secret:}")
    private String defaultFeishuAppSecret;

    /** 飞书 tenant_access_token 缓存：appId -> (token, 过期时间戳) */
    private static final ConcurrentHashMap<String, CachedFeishuToken> FEISHU_TOKEN_CACHE = new ConcurrentHashMap<>();

    private static class CachedFeishuToken {
        final String token;
        final long expiresAtMs;

        CachedFeishuToken(String token, long expiresAtMs) {
            this.token = token;
            this.expiresAtMs = expiresAtMs;
        }
    }

    public NotificationService(
            NotificationRuleService notificationRuleService,
            NotificationRepository notificationRepository,
            RestTemplate restTemplate,
            @org.springframework.beans.factory.annotation.Autowired(required = false) JavaMailSender mailSender,
            @org.springframework.beans.factory.annotation.Autowired(required = false) NotificationChannelConfigService channelConfigService) {
        this.notificationRuleService = notificationRuleService;
        this.notificationRepository = notificationRepository;
        this.restTemplate = restTemplate;
        this.mailSender = mailSender;
        this.channelConfigService = channelConfigService;
    }
    
    /**
     * 检查内容是否匹配通知规则并发送通知
     */
    public void checkAndNotify(Content content) {
        List<NotificationRule> enabledRules = notificationRuleService.findAllEnabled();
        if (enabledRules.isEmpty()) {
            return;
        }
        for (NotificationRule rule : enabledRules) {
            if (!matchesRule(content, rule)) {
                log.debug("通知规则未匹配: rule={}, contentUser={}", rule.getName(),
                    content.getUser() != null ? content.getUser().getId() : null);
                continue;
            }
            if (isInQuietHours(rule)) {
                log.debug("通知规则在免打扰时段跳过: rule={}", rule.getName());
                continue;
            }
            log.info("通知规则匹配，开始下发: rule={}, contentId={}", rule.getName(), content.getId());
            sendNotification(content, rule);
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
            case "QQ_GROUP" -> matchesQqGroupUser(content, config);
            case "FEISHU" -> matchesFeishuUser(content, config);
            default -> false;
        };
    }

    /**
     * 飞书规则：仅当内容作者（追踪用户）在配置的 userIds 列表中时匹配
     */
    @SuppressWarnings("unchecked")
    private boolean matchesFeishuUser(Content content, Map<String, Object> config) {
        Object userIdsObj = config.get("userIds");
        if (userIdsObj == null || !(userIdsObj instanceof List)) {
            return false;
        }
        String contentUserId = content.getUser() != null ? content.getUser().getId().toString() : null;
        if (contentUserId == null) return false;
        for (Object o : (List<?>) userIdsObj) {
            if (contentUserId.equals(o != null ? o.toString() : null)) {
                return true;
            }
        }
        return false;
    }

    /**
     * QQ 群规则：仅当内容作者（追踪用户）在配置的 userIds 列表中时匹配
     */
    @SuppressWarnings("unchecked")
    private boolean matchesQqGroupUser(Content content, Map<String, Object> config) {
        Object userIdsObj = config.get("userIds");
        if (userIdsObj == null || !(userIdsObj instanceof List)) {
            return false;
        }
        String contentUserId = content.getUser() != null ? content.getUser().getId().toString() : null;
        if (contentUserId == null) return false;
        for (Object o : (List<?>) userIdsObj) {
            if (contentUserId.equals(o != null ? o.toString() : null)) {
                return true;
            }
        }
        return false;
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
     * 若规则 config 中有 channelConfigId，则从通道配置表加载并合并为发送用 config（规则内字段优先）。
     */
    private Map<String, Object> resolveConfigForSend(NotificationRule rule) {
        Map<String, Object> config = rule.getConfig();
        if (config == null) return null;
        Object channelConfigIdObj = config.get("channelConfigId");
        if (channelConfigService == null || channelConfigIdObj == null || channelConfigIdObj.toString().isBlank()) {
            return config;
        }
        try {
            UUID channelConfigId = UUID.fromString(channelConfigIdObj.toString().trim());
            var channelConfig = channelConfigService.getById(channelConfigId);
            Map<String, Object> channelConfigMap = channelConfig.getConfig();
            if (channelConfigMap == null || channelConfigMap.isEmpty()) return config;
            Map<String, Object> merged = new HashMap<>(channelConfigMap);
            merged.putAll(config); // 规则内字段（如 userIds）覆盖通道配置
            return merged;
        } catch (Exception e) {
            log.warn("解析 channelConfigId 失败，使用规则 config: rule={}, channelConfigId={}", rule.getName(), channelConfigIdObj, e);
            return config;
        }
    }

    /**
     * 发送通知
     */
    private void sendNotification(Content content, NotificationRule rule) {
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return;
        }
        Map<String, Object> sendConfig = resolveConfigForSend(rule);
        if (sendConfig == null) return;

        // QQ_GROUP / FEISHU 规则类型：不依赖 notificationMethods，直接按规则类型发送
        if ("QQ_GROUP".equalsIgnoreCase(rule.getRuleType())) {
            try {
                sendQqGroupNotification(content, rule, sendConfig);
            } catch (Exception e) {
                log.error("发送 QQ 群通知失败: rule={}", rule.getName(), e);
            }
            return;
        }
        if ("FEISHU".equalsIgnoreCase(rule.getRuleType())) {
            try {
                sendFeishuNotification(content, rule, sendConfig);
            } catch (Exception e) {
                log.error("发送飞书通知失败: rule={}", rule.getName(), e);
            }
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
                    case "QQ_GROUP" -> sendQqGroupNotification(content, rule, config);
                    default -> log.warn("未知的通知方式: {}", method);
                }
            } catch (Exception e) {
                log.error("发送通知失败: method={}, rule={}", method, rule.getName(), e);
            }
        }
    }

    /**
     * 发送到 QQ 群，支持多种机器人对接：
     * - go-cqhttp: POST /send_group_msg，body { group_id, message }
     * - mirai: POST /sendGroupMessage（mirai-api-http），body { sessionKey, target, messageChain: [{ type: "Plain", text }] }
     * config: qqGroupId, qqApiUrl, messageTemplate, qqBotType（go-cqhttp|mirai）, qqSessionKey（Mirai 必填）
     */
    @SuppressWarnings("unchecked")
    private void sendQqGroupNotification(Content content, NotificationRule rule, Map<String, Object> config) {
        String groupIdStr = config.get("qqGroupId") != null ? config.get("qqGroupId").toString().trim() : null;
        if (groupIdStr == null || groupIdStr.isEmpty()) {
            log.warn("QQ 群号未配置: rule={}", rule.getName());
            return;
        }
        String baseUrl = config.get("qqApiUrl") != null ? config.get("qqApiUrl").toString().trim() : null;
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = defaultQqApiUrl != null ? defaultQqApiUrl.trim() : null;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            log.warn("QQ Bot API 地址未配置（规则 config.qqApiUrl 与 app.qq.default-api-url）: rule={}", rule.getName());
            return;
        }
        String botType = config.get("qqBotType") != null ? config.get("qqBotType").toString().trim() : "";
        if (botType.isEmpty()) botType = "go-cqhttp";

        String template = config.get("messageTemplate") != null ? config.get("messageTemplate").toString() : null;
        if (template == null || template.isEmpty()) {
            template = "【新内容】{title}\n作者: {author}\n平台: {platform}\n链接: {url}";
        }
        String message = template
            .replace("{title}", content.getTitle() != null ? content.getTitle() : "无标题")
            .replace("{author}", content.getUser() != null ? content.getUser().getDisplayName() != null ? content.getUser().getDisplayName() : content.getUser().getUsername() : "—")
            .replace("{platform}", content.getPlatform() != null ? content.getPlatform().getName() : "—")
            .replace("{url}", content.getUrl() != null ? content.getUrl() : "");

        String base = baseUrl.replaceAll("/+$", "");
        if ("mirai".equalsIgnoreCase(botType)) {
            String sessionKey = config.get("qqSessionKey") != null ? config.get("qqSessionKey").toString().trim() : null;
            if (sessionKey == null || sessionKey.isEmpty()) {
                log.warn("Mirai Session Key 未配置: rule={}", rule.getName());
                return;
            }
            long target;
            try {
                target = Long.parseLong(groupIdStr);
            } catch (NumberFormatException e) {
                log.warn("QQ 群号格式无效: groupId={}", groupIdStr);
                return;
            }
            List<Map<String, String>> messageChain = List.of(Map.of("type", "Plain", "text", message));
            Map<String, Object> body = Map.of(
                "sessionKey", sessionKey,
                "target", target,
                "messageChain", messageChain
            );
            String apiUrl = base + "/sendGroupMessage";
            try {
                restTemplate.postForObject(apiUrl, body, String.class);
                log.info("QQ 群通知已发送(Mirai): rule={}, groupId={}, contentId={}", rule.getName(), target, content.getId());
                saveNotificationRecord(content, rule, "QQ_GROUP");
            } catch (Exception e) {
                log.error("Mirai 发送群消息失败: rule={}, api={}", rule.getName(), apiUrl, e);
            }
            return;
        }

        // go-cqhttp（默认）
        String apiUrl = base + "/send_group_msg";
        try {
            long groupId = Long.parseLong(groupIdStr);
            Map<String, Object> body = Map.of("group_id", groupId, "message", message);
            restTemplate.postForObject(apiUrl, body, String.class);
            log.info("QQ 群通知已发送(go-cqhttp): rule={}, groupId={}, contentId={}", rule.getName(), groupId, content.getId());
            saveNotificationRecord(content, rule, "QQ_GROUP");
        } catch (NumberFormatException e) {
            log.warn("QQ 群号格式无效，尝试字符串: groupId={}", groupIdStr);
            Map<String, Object> body = Map.of("group_id", groupIdStr, "message", message);
            restTemplate.postForObject(apiUrl, body, String.class);
            log.info("QQ 群通知已发送(go-cqhttp): rule={}, groupId={}, contentId={}", rule.getName(), groupIdStr, content.getId());
            saveNotificationRecord(content, rule, "QQ_GROUP");
        }
    }

    /**
     * 发送到飞书（群聊/私聊），使用飞书开放平台「发送消息」API。
     * config: userIds, feishuAppId, feishuAppSecret, feishuReceiveId（群 chat_id 或用户 open_id）, feishuReceiveIdType（chat_id/open_id/user_id）, messageTemplate
     */
    @SuppressWarnings("unchecked")
    private void sendFeishuNotification(Content content, NotificationRule rule, Map<String, Object> config) {
        String appId = config.get("feishuAppId") != null ? config.get("feishuAppId").toString().trim() : null;
        if (appId == null || appId.isEmpty()) {
            appId = defaultFeishuAppId != null ? defaultFeishuAppId.trim() : null;
        }
        String appSecret = config.get("feishuAppSecret") != null ? config.get("feishuAppSecret").toString().trim() : null;
        if (appSecret == null || appSecret.isEmpty()) {
            appSecret = defaultFeishuAppSecret != null ? defaultFeishuAppSecret.trim() : null;
        }
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            log.warn("飞书 App ID / App Secret 未配置（规则 config 或 app.feishu.default-*）: rule={}", rule.getName());
            return;
        }
        String receiveId = config.get("feishuReceiveId") != null ? config.get("feishuReceiveId").toString().trim() : null;
        if (receiveId == null || receiveId.isEmpty()) {
            log.warn("飞书接收 ID（群聊/会话）未配置: rule={}", rule.getName());
            return;
        }
        String receiveIdType = config.get("feishuReceiveIdType") != null ? config.get("feishuReceiveIdType").toString().trim() : "chat_id";
        if (receiveIdType.isEmpty()) receiveIdType = "chat_id";

        String template = config.get("messageTemplate") != null ? config.get("messageTemplate").toString() : null;
        if (template == null || template.isEmpty()) {
            template = "【新内容】{title}\n作者: {author}\n平台: {platform}\n链接: {url}";
        }
        String text = template
            .replace("{title}", content.getTitle() != null ? content.getTitle() : "无标题")
            .replace("{author}", content.getUser() != null ? (content.getUser().getDisplayName() != null ? content.getUser().getDisplayName() : content.getUser().getUsername()) : "—")
            .replace("{platform}", content.getPlatform() != null ? content.getPlatform().getName() : "—")
            .replace("{url}", content.getUrl() != null ? content.getUrl() : "");

        String token = getFeishuTenantAccessToken(appId, appSecret);
        if (token == null) {
            log.error("获取飞书 tenant_access_token 失败: rule={}", rule.getName());
            return;
        }

        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType;
        Map<String, Object> body = Map.of(
            "receive_id", receiveId,
            "msg_type", "text",
            "content", "{\"text\":\"" + escapeJsonString(text) + "\"}"
        );
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, Map.class);
            log.info("飞书通知已发送: rule={}, receiveId={}, contentId={}", rule.getName(), receiveId, content.getId());
            saveNotificationRecord(content, rule, "FEISHU");
        } catch (Exception e) {
            log.error("飞书发送消息失败: rule={}, receiveId={}", rule.getName(), receiveId, e);
        }
    }

    private String escapeJsonString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * 发送测试消息到规则配置的机器人（QQ 群 / 飞书），用于验证通道是否可用。
     * @return null 表示成功，非 null 为错误信息
     */
    public String sendTestMessage(NotificationRule rule) {
        Map<String, Object> config = rule.getConfig();
        if (config == null) {
            return "规则未配置";
        }
        String ruleType = rule.getRuleType();
        if (ruleType == null) {
            return "规则类型未知";
        }
        return switch (ruleType.toUpperCase()) {
            case "QQ_GROUP" -> sendQqGroupTestMessage(rule, config);
            case "FEISHU" -> sendFeishuTestMessage(rule, config);
            default -> "该规则类型不支持测试（仅支持 QQ 群、飞书）";
        };
    }

    /**
     * 按规则类型 + 配置直接发送测试消息（不依赖已保存规则），用于页面上「测试某种规则类型是否可行」。
     * @param ruleType QQ_GROUP 或 FEISHU
     * @param config 对应类型的 config 字段（qqGroupId、qqApiUrl 等 或 feishuAppId、feishuReceiveId 等）
     * @return null 表示成功，非 null 为错误信息
     */
    public String sendTestMessageWithConfig(String ruleType, Map<String, Object> config) {
        if (ruleType == null || ruleType.isBlank()) {
            return "请选择规则类型";
        }
        if (config == null) {
            return "请填写配置";
        }
        NotificationRule dummy = new NotificationRule();
        dummy.setRuleType(ruleType.trim());
        dummy.setConfig(config);
        dummy.setName("测试");
        return sendTestMessage(dummy);
    }

    private static final String TEST_MESSAGE = "【测试】这是一条来自内容聚合系统的测试消息。若收到则说明通知通道正常。";

    @SuppressWarnings("unchecked")
    private String sendQqGroupTestMessage(NotificationRule rule, Map<String, Object> config) {
        String groupIdStr = config.get("qqGroupId") != null ? config.get("qqGroupId").toString().trim() : null;
        if (groupIdStr == null || groupIdStr.isEmpty()) {
            return "QQ 群号未配置";
        }
        String baseUrl = config.get("qqApiUrl") != null ? config.get("qqApiUrl").toString().trim() : null;
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = defaultQqApiUrl != null ? defaultQqApiUrl.trim() : null;
        }
        if (baseUrl == null || baseUrl.isEmpty()) {
            return "QQ Bot API 地址未配置（请在规则中填写 qqApiUrl 或配置 app.qq.default-api-url）";
        }
        String botType = config.get("qqBotType") != null ? config.get("qqBotType").toString().trim() : "";
        if (botType.isEmpty()) botType = "go-cqhttp";
        String base = baseUrl.replaceAll("/+$", "");

        if ("mirai".equalsIgnoreCase(botType)) {
            String sessionKey = config.get("qqSessionKey") != null ? config.get("qqSessionKey").toString().trim() : null;
            if (sessionKey == null || sessionKey.isEmpty()) {
                return "Mirai Session Key 未配置";
            }
            long target;
            try {
                target = Long.parseLong(groupIdStr);
            } catch (NumberFormatException e) {
                return "QQ 群号格式无效";
            }
            List<Map<String, String>> messageChain = List.of(Map.of("type", "Plain", "text", TEST_MESSAGE));
            Map<String, Object> body = Map.of("sessionKey", sessionKey, "target", target, "messageChain", messageChain);
            String apiUrl = base + "/sendGroupMessage";
            try {
                restTemplate.postForObject(apiUrl, body, String.class);
                log.info("QQ 群测试消息已发送(Mirai): rule={}", rule.getName());
                return null;
            } catch (Exception e) {
                log.warn("QQ 群测试发送失败(Mirai): rule={}", rule.getName(), e);
                return e.getMessage() != null ? e.getMessage() : "请求失败";
            }
        }

        String apiUrl = base + "/send_group_msg";
        try {
            long groupId = Long.parseLong(groupIdStr);
            Map<String, Object> body = Map.of("group_id", groupId, "message", TEST_MESSAGE);
            restTemplate.postForObject(apiUrl, body, String.class);
            log.info("QQ 群测试消息已发送(go-cqhttp): rule={}", rule.getName());
            return null;
        } catch (NumberFormatException e) {
            Map<String, Object> body = Map.of("group_id", groupIdStr, "message", TEST_MESSAGE);
            try {
                restTemplate.postForObject(apiUrl, body, String.class);
                log.info("QQ 群测试消息已发送(go-cqhttp): rule={}", rule.getName());
                return null;
            } catch (Exception ex) {
                log.warn("QQ 群测试发送失败(go-cqhttp): rule={}", rule.getName(), ex);
                return ex.getMessage() != null ? ex.getMessage() : "请求失败";
            }
        } catch (Exception e) {
            log.warn("QQ 群测试发送失败(go-cqhttp): rule={}", rule.getName(), e);
            return e.getMessage() != null ? e.getMessage() : "请求失败";
        }
    }

    @SuppressWarnings("unchecked")
    private String sendFeishuTestMessage(NotificationRule rule, Map<String, Object> config) {
        String appId = config.get("feishuAppId") != null ? config.get("feishuAppId").toString().trim() : null;
        if (appId == null || appId.isEmpty()) appId = defaultFeishuAppId != null ? defaultFeishuAppId.trim() : null;
        String appSecret = config.get("feishuAppSecret") != null ? config.get("feishuAppSecret").toString().trim() : null;
        if (appSecret == null || appSecret.isEmpty()) appSecret = defaultFeishuAppSecret != null ? defaultFeishuAppSecret.trim() : null;
        if (appId == null || appId.isEmpty() || appSecret == null || appSecret.isEmpty()) {
            return "飞书 App ID / App Secret 未配置";
        }
        String receiveId = config.get("feishuReceiveId") != null ? config.get("feishuReceiveId").toString().trim() : null;
        if (receiveId == null || receiveId.isEmpty()) {
            return "飞书接收 ID 未配置";
        }
        String receiveIdType = config.get("feishuReceiveIdType") != null ? config.get("feishuReceiveIdType").toString().trim() : "chat_id";
        if (receiveIdType.isEmpty()) receiveIdType = "chat_id";

        String token = getFeishuTenantAccessToken(appId, appSecret);
        if (token == null) {
            return "获取飞书 tenant_access_token 失败，请检查 App ID / App Secret";
        }
        String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=" + receiveIdType;
        Map<String, Object> body = Map.of(
            "receive_id", receiveId,
            "msg_type", "text",
            "content", "{\"text\":\"" + escapeJsonString(TEST_MESSAGE) + "\"}"
        );
        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<Map<String, Object>> entity = new org.springframework.http.HttpEntity<>(body, headers);
            restTemplate.postForEntity(url, entity, Map.class);
            log.info("飞书测试消息已发送: rule={}", rule.getName());
            return null;
        } catch (Exception e) {
            log.warn("飞书测试发送失败: rule={}", rule.getName(), e);
            return e.getMessage() != null ? e.getMessage() : "请求失败";
        }
    }

    /**
     * 获取飞书 tenant_access_token，带缓存（过期前 5 分钟刷新）
     */
    private String getFeishuTenantAccessToken(String appId, String appSecret) {
        long nowMs = Instant.now().toEpochMilli();
        CachedFeishuToken cached = FEISHU_TOKEN_CACHE.get(appId);
        if (cached != null && cached.expiresAtMs - 5 * 60 * 1000 > nowMs) {
            return cached.token;
        }
        try {
            Map<String, String> req = Map.of("app_id", appId, "app_secret", appSecret);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = restTemplate.postForObject(
                "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal",
                req,
                Map.class
            );
            if (resp == null) return null;
            String token = (String) resp.get("tenant_access_token");
            Number expire = (Number) resp.get("expire");
            if (token == null || expire == null) return null;
            long expiresAtMs = nowMs + expire.longValue() * 1000;
            FEISHU_TOKEN_CACHE.put(appId, new CachedFeishuToken(token, expiresAtMs));
            return token;
        } catch (Exception e) {
            log.warn("飞书获取 tenant_access_token 失败: appId={}", appId, e);
            return null;
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
