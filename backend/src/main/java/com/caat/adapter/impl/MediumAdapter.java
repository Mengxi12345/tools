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
 * Medium 平台适配器
 */
@Slf4j
@Component
public class MediumAdapter implements PlatformAdapter {
    
    private static final String PLATFORM_TYPE = "MEDIUM";
    private static final String API_BASE_URL = "https://api.medium.com/v1";
    
    private final RestTemplate restTemplate;
    
    public MediumAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }
    
    @Override
    public boolean testConnection(Map<String, Object> config) throws BusinessException {
        try {
            String accessToken = (String) config.get("accessToken");
            if (accessToken == null || accessToken.isEmpty()) {
                throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "Medium access token 未配置");
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
            log.error("Medium 连接测试失败", e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "Medium 连接测试失败: " + e.getMessage());
        }
    }
    
    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        try {
            String accessToken = (String) config.get("accessToken");
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Medium API: GET /users/{userId}
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/users/" + userId,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);
            }
            
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            if (data == null) {
                throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);
            }
            
            PlatformUser user = new PlatformUser();
            user.setUserId(data.get("id").toString());
            user.setUsername((String) data.get("username"));
            user.setDisplayName((String) data.get("name"));
            user.setAvatarUrl((String) data.get("imageUrl"));
            
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 Medium 用户信息失败: userId={}", userId, e);
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
            
            // Medium API: GET /users/{userId}/publications
            String url = API_BASE_URL + "/users/" + userId + "/articles";
            if (limit != null && limit > 0) {
                url += "?limit=" + Math.min(limit, 100);
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
            
            List<Map<String, Object>> articles = (List<Map<String, Object>>) response.getBody().get("data");
            if (articles == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<PlatformContent> contents = new ArrayList<>();
            for (Map<String, Object> article : articles) {
                try {
                    PlatformContent content = convertToPlatformContent(article);
                    
                    // 时间过滤
                    if (startTime != null && content.getPublishedAt().isBefore(startTime)) {
                        continue;
                    }
                    if (endTime != null && content.getPublishedAt().isAfter(endTime)) {
                        continue;
                    }
                    
                    contents.add(content);
                } catch (Exception e) {
                    log.warn("转换 Medium 文章失败", e);
                }
            }
            
            return FetchResult.builder()
                    .contents(contents)
                    .hasMore(false)
                    .build();
        } catch (Exception e) {
            log.error("获取 Medium 用户内容失败: userId={}", userId, e);
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
        headers.set("Content-Type", "application/json");
        return headers;
    }
    
    private PlatformContent convertToPlatformContent(Map<String, Object> article) {
        PlatformContent content = new PlatformContent();
        content.setContentId(article.get("id").toString());
        content.setTitle((String) article.get("title"));
        
        // Medium API 返回的内容可能需要额外请求获取正文
        String url = (String) article.get("url");
        content.setUrl(url != null ? url : "");
        
        // 发布时间
        String publishedAtStr = (String) article.get("publishedAt");
        if (publishedAtStr != null) {
            try {
                LocalDateTime publishedAt = LocalDateTime.parse(
                        publishedAtStr.substring(0, 19),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                );
                content.setPublishedAt(publishedAt);
            } catch (Exception e) {
                content.setPublishedAt(LocalDateTime.now());
            }
        } else {
            content.setPublishedAt(LocalDateTime.now());
        }
        
        content.setContentType(PlatformContent.ContentType.TEXT);
        
        // 标签（存储在 metadata 中）
        List<String> tags = (List<String>) article.get("tags");
        if (tags != null) {
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("tags", tags);
            content.setMetadata(metadata);
        }
        
        return content;
    }
}
