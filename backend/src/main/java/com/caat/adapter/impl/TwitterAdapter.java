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
 * Twitter/X 平台适配器
 */
@Slf4j
@Component
public class TwitterAdapter implements PlatformAdapter {
    
    private static final String PLATFORM_TYPE = "TWITTER";
    private static final String API_BASE_URL = "https://api.twitter.com/2";
    
    private final RestTemplate restTemplate;
    
    public TwitterAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }
    
    @Override
    public boolean testConnection(Map<String, Object> config) throws BusinessException {
        try {
            String bearerToken = (String) config.get("bearerToken");
            if (bearerToken == null || bearerToken.isEmpty()) {
                throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "Twitter Bearer Token 未配置");
            }
            
            HttpHeaders headers = createHeaders(bearerToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/users/me",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Twitter 连接测试失败", e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "Twitter 连接测试失败: " + e.getMessage());
        }
    }
    
    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        try {
            String bearerToken = (String) config.get("bearerToken");
            HttpHeaders headers = createHeaders(bearerToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Twitter API v2: GET /users/by/username/{username} 或 /users/{id}
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/users/by/username/" + userId + "?user.fields=name,profile_image_url",
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
            user.setUsername((String) data.get("username"));
            user.setDisplayName((String) data.get("name"));
            user.setAvatarUrl((String) data.get("profile_image_url"));
            
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 Twitter 用户信息失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND, "获取用户信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public FetchResult getUserContents(String userId, Map<String, Object> config, 
                                      LocalDateTime startTime, LocalDateTime endTime, 
                                      String cursor, Integer limit) throws BusinessException {
        try {
            String bearerToken = (String) config.get("bearerToken");
            HttpHeaders headers = createHeaders(bearerToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Twitter API v2: GET /users/{id}/tweets
            String url = API_BASE_URL + "/users/" + userId + "/tweets";
            url += "?tweet.fields=created_at,text,public_metrics";
            if (limit != null && limit > 0) {
                url += "&max_results=" + Math.min(limit, 100);
            }
            if (cursor != null && !cursor.isEmpty()) {
                url += "&pagination_token=" + cursor;
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
            List<Map<String, Object>> tweets = (List<Map<String, Object>>) data.get("data");
            if (tweets == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<PlatformContent> contents = new ArrayList<>();
            Map<String, Object> meta = (Map<String, Object>) data.get("meta");
            String nextToken = meta != null ? (String) meta.get("next_token") : null;
            
            for (Map<String, Object> tweet : tweets) {
                try {
                    PlatformContent content = convertToPlatformContent(tweet, userId);
                    
                    // 时间过滤
                    if (startTime != null && content.getPublishedAt().isBefore(startTime)) {
                        continue;
                    }
                    if (endTime != null && content.getPublishedAt().isAfter(endTime)) {
                        continue;
                    }
                    
                    contents.add(content);
                } catch (Exception e) {
                    log.warn("转换 Twitter 推文失败", e);
                }
            }
            
            return FetchResult.builder()
                    .contents(contents)
                    .nextCursor(nextToken)
                    .hasMore(nextToken != null)
                    .build();
        } catch (Exception e) {
            log.error("获取 Twitter 用户内容失败: userId={}", userId, e);
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
    
    private HttpHeaders createHeaders(String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + bearerToken);
        return headers;
    }
    
    private PlatformContent convertToPlatformContent(Map<String, Object> tweet, String userId) {
        PlatformContent content = new PlatformContent();
        content.setContentId(tweet.get("id").toString());
        content.setTitle(""); // Twitter 推文没有标题
        content.setBody((String) tweet.get("text"));
        
        String tweetId = tweet.get("id").toString();
        content.setUrl("https://twitter.com/" + userId + "/status/" + tweetId);
        
        // 发布时间
        String createdAt = (String) tweet.get("created_at");
        if (createdAt != null) {
            try {
                LocalDateTime publishedAt = LocalDateTime.parse(
                        createdAt.substring(0, 19),
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
        
        // 互动数据（存储在 metadata 中）
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Object> publicMetrics = (Map<String, Object>) tweet.get("public_metrics");
        if (publicMetrics != null) {
            metadata.put("likeCount", publicMetrics.get("like_count"));
            metadata.put("retweetCount", publicMetrics.get("retweet_count"));
            metadata.put("replyCount", publicMetrics.get("reply_count"));
        }
        content.setMetadata(metadata);
        
        return content;
    }
}
