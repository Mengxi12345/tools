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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reddit 平台适配器
 */
@Slf4j
@Component
public class RedditAdapter implements PlatformAdapter {
    
    private static final String PLATFORM_TYPE = "REDDIT";
    private static final String API_BASE_URL = "https://oauth.reddit.com";
    
    private final RestTemplate restTemplate;
    
    public RedditAdapter(RestTemplate restTemplate) {
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
                throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "Reddit access token 未配置");
            }
            
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/api/v1/me",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("Reddit 连接测试失败", e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "Reddit 连接测试失败: " + e.getMessage());
        }
    }
    
    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        try {
            String accessToken = (String) config.get("accessToken");
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // Reddit API: GET /api/v1/me 或 /user/{username}/about
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/user/" + userId + "/about.json",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);
            }
            
            Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) response.getBody()).get("data");
            if (data == null) {
                throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);
            }
            
            PlatformUser user = new PlatformUser();
            user.setUserId(data.get("id").toString());
            user.setUsername((String) data.get("name"));
            user.setDisplayName((String) data.get("name"));
            user.setAvatarUrl((String) data.get("icon_img"));
            
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 Reddit 用户信息失败: userId={}", userId, e);
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
            
            // Reddit API: GET /user/{username}/submitted
            String url = API_BASE_URL + "/user/" + userId + "/submitted.json";
            if (limit != null && limit > 0) {
                url += "?limit=" + Math.min(limit, 100);
            }
            if (cursor != null && !cursor.isEmpty()) {
                url += (url.contains("?") ? "&" : "?") + "after=" + cursor;
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
            
            Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
            if (data == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<Map<String, Object>> children = (List<Map<String, Object>>) data.get("children");
            if (children == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<PlatformContent> contents = new ArrayList<>();
            String nextCursor = (String) data.get("after");
            
            for (Map<String, Object> child : children) {
                try {
                    Map<String, Object> postData = (Map<String, Object>) child.get("data");
                    if (postData == null) {
                        continue;
                    }
                    
                    PlatformContent content = convertToPlatformContent(postData);
                    
                    // 时间过滤
                    if (startTime != null && content.getPublishedAt().isBefore(startTime)) {
                        continue;
                    }
                    if (endTime != null && content.getPublishedAt().isAfter(endTime)) {
                        continue;
                    }
                    
                    contents.add(content);
                } catch (Exception e) {
                    log.warn("转换 Reddit 帖子失败", e);
                }
            }
            
            return FetchResult.builder()
                    .contents(contents)
                    .nextCursor(nextCursor)
                    .hasMore(nextCursor != null)
                    .build();
        } catch (Exception e) {
            log.error("获取 Reddit 用户内容失败: userId={}", userId, e);
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
    
    private PlatformContent convertToPlatformContent(Map<String, Object> postData) {
        PlatformContent content = new PlatformContent();
        content.setContentId(postData.get("id").toString());
        content.setTitle((String) postData.get("title"));
        content.setBody((String) postData.get("selftext"));
        
        String url = (String) postData.get("url");
        content.setUrl(url != null ? url : "");
        
        // Reddit 使用 Unix 时间戳
        Double createdUtc = ((Number) postData.get("created_utc")).doubleValue();
        LocalDateTime publishedAt = LocalDateTime.ofEpochSecond(
                createdUtc.longValue(),
                0,
                ZoneOffset.UTC
        );
        content.setPublishedAt(publishedAt);
        
        // 判断内容类型
        String postHint = (String) postData.get("post_hint");
        if (postHint != null) {
            if (postHint.contains("image")) {
                content.setContentType(PlatformContent.ContentType.IMAGE);
            } else if (postHint.contains("video")) {
                content.setContentType(PlatformContent.ContentType.VIDEO);
            } else if (url != null && !url.isEmpty() && !url.startsWith("https://www.reddit.com")) {
                content.setContentType(PlatformContent.ContentType.LINK);
            } else {
                content.setContentType(PlatformContent.ContentType.TEXT);
            }
        } else {
            content.setContentType(PlatformContent.ContentType.TEXT);
        }
        
        // 子版块（subreddit）作为标签（存储在 metadata 中）
        String subreddit = (String) postData.get("subreddit");
        if (subreddit != null) {
            Map<String, Object> metadata = new java.util.HashMap<>();
            metadata.put("subreddit", subreddit);
            metadata.put("tags", List.of("r/" + subreddit));
            content.setMetadata(metadata);
        }
        
        return content;
    }
}
