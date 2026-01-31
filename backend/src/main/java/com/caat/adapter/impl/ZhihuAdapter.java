package com.caat.adapter.impl;

import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.FetchResult;
import com.caat.adapter.model.PlatformContent;
import com.caat.adapter.model.PlatformUser;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知乎平台适配器
 */
@Slf4j
@Component
public class ZhihuAdapter implements PlatformAdapter {
    
    private static final String PLATFORM_TYPE = "ZHIHU";
    private static final String API_BASE_URL = "https://www.zhihu.com/api/v4";
    
    private final RestTemplate restTemplate;
    
    public ZhihuAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }
    
    @Override
    public boolean testConnection(Map<String, Object> config) throws BusinessException {
        try {
            // 知乎 API 需要认证，这里简化处理
            String accessToken = (String) config.get("accessToken");
            if (accessToken == null || accessToken.isEmpty()) {
                throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "知乎 access token 未配置");
            }
            
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/me",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("知乎连接测试失败", e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "知乎连接测试失败: " + e.getMessage());
        }
    }
    
    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        try {
            String accessToken = (String) config.get("accessToken");
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // 知乎 API: GET /members/{userId}
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/members/" + userId,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);
            }
            
            Map<String, Object> data = (Map<String, Object>) response.getBody();
            
            PlatformUser user = new PlatformUser();
            user.setUserId(data.get("id").toString());
            user.setUsername((String) data.get("name"));
            user.setDisplayName((String) data.get("name"));
            user.setAvatarUrl((String) data.get("avatar_url"));
            
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取知乎用户信息失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND, "获取用户信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public FetchResult getUserContents(String userId, Map<String, Object> config, 
                                      LocalDateTime startTime, LocalDateTime endTime, 
                                      String cursor, Integer limit) throws BusinessException {
        try {
            String accessToken = (String) config.get("accessToken");
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // 知乎 API: GET /members/{userId}/answers
            String url = API_BASE_URL + "/members/" + userId + "/answers";
            if (limit != null && limit > 0) {
                url += "?limit=" + Math.min(limit, 20);
            }
            if (cursor != null && !cursor.isEmpty()) {
                url += (url.contains("?") ? "&" : "?") + "offset=" + cursor;
            }
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            Map<String, Object> data = (Map<String, Object>) response.getBody();
            List<Map<String, Object>> answers = (List<Map<String, Object>>) data.get("data");
            if (answers == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<PlatformContent> contents = new ArrayList<>();
            String nextCursor = data.get("paging") != null ? 
                    ((Map<String, Object>) data.get("paging")).get("next").toString() : null;
            
            for (Map<String, Object> answer : answers) {
                try {
                    PlatformContent content = convertToPlatformContent(answer);
                    
                    // 时间过滤
                    if (startTime != null && content.getPublishedAt().isBefore(startTime)) {
                        continue;
                    }
                    if (endTime != null && content.getPublishedAt().isAfter(endTime)) {
                        continue;
                    }
                    
                    contents.add(content);
                } catch (Exception e) {
                    log.warn("转换知乎回答失败", e);
                }
            }
            
            return FetchResult.builder()
                    .contents(contents)
                    .nextCursor(nextCursor)
                    .hasMore(nextCursor != null)
                    .build();
        } catch (Exception e) {
            log.error("获取知乎用户内容失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "获取内容失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean validateUserId(String userId, Map<String, Object> config) throws BusinessException {
        try {
            PlatformUser user = getUserInfo(userId, config);
            return user != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    private HttpHeaders createHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.set("User-Agent", "ContentAggregator/1.0");
        return headers;
    }
    
    private PlatformContent convertToPlatformContent(Map<String, Object> answer) {
        PlatformContent content = new PlatformContent();
        content.setContentId(answer.get("id").toString());
        
        Map<String, Object> question = (Map<String, Object>) answer.get("question");
        if (question != null) {
            content.setTitle((String) question.get("title"));
        }
        
        Map<String, Object> excerpt = (Map<String, Object>) answer.get("excerpt");
        if (excerpt != null) {
            content.setBody((String) excerpt.get("text"));
        }
        
        String url = (String) answer.get("url");
        content.setUrl(url != null ? url : "");
        
        // 发布时间
        Long createdTime = ((Number) answer.get("created_time")).longValue();
        LocalDateTime publishedAt = LocalDateTime.ofEpochSecond(createdTime, 0, java.time.ZoneOffset.UTC);
        content.setPublishedAt(publishedAt);
        
        content.setContentType(PlatformContent.ContentType.TEXT);
        
        // 标签（存储在 metadata 中）
        Map<String, Object> metadata = new HashMap<>();
        List<String> topics = (List<String>) answer.get("topics");
        if (topics != null) {
            metadata.put("topics", topics);
        }
        content.setMetadata(metadata);
        
        return content;
    }
}
