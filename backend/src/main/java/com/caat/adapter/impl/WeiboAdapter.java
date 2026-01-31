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
 * 微博平台适配器
 */
@Slf4j
@Component
public class WeiboAdapter implements PlatformAdapter {
    
    private static final String PLATFORM_TYPE = "WEIBO";
    private static final String API_BASE_URL = "https://api.weibo.com/2";
    
    private final RestTemplate restTemplate;
    
    public WeiboAdapter(RestTemplate restTemplate) {
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
                throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "微博 access token 未配置");
            }
            
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/account/get_uid.json",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("微博连接测试失败", e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "微博连接测试失败: " + e.getMessage());
        }
    }
    
    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        try {
            String accessToken = (String) config.get("accessToken");
            HttpHeaders headers = createHeaders(accessToken);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // 微博 API: GET /users/show.json
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/users/show.json?uid=" + userId,
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
            user.setUsername((String) data.get("screen_name"));
            user.setDisplayName((String) data.get("name"));
            user.setAvatarUrl((String) data.get("avatar_large"));
            
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取微博用户信息失败: userId={}", userId, e);
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
            
            // 微博 API: GET /statuses/user_timeline.json
            String url = API_BASE_URL + "/statuses/user_timeline.json";
            url += "?uid=" + userId;
            if (limit != null && limit > 0) {
                url += "&count=" + Math.min(limit, 200);
            }
            if (cursor != null && !cursor.isEmpty()) {
                url += "&page=" + cursor;
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
            
            List<Map<String, Object>> statuses = (List<Map<String, Object>>) response.getBody().get("statuses");
            if (statuses == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<PlatformContent> contents = new ArrayList<>();
            String nextPage = cursor != null ? String.valueOf(Integer.parseInt(cursor) + 1) : "2";
            
            for (Map<String, Object> status : statuses) {
                try {
                    PlatformContent content = convertToPlatformContent(status);
                    
                    // 时间过滤
                    if (startTime != null && content.getPublishedAt().isBefore(startTime)) {
                        continue;
                    }
                    if (endTime != null && content.getPublishedAt().isAfter(endTime)) {
                        continue;
                    }
                    
                    contents.add(content);
                } catch (Exception e) {
                    log.warn("转换微博内容失败", e);
                }
            }
            
            return FetchResult.builder()
                    .contents(contents)
                    .nextCursor(nextPage)
                    .hasMore(statuses.size() == limit)
                    .build();
        } catch (Exception e) {
            log.error("获取微博用户内容失败: userId={}", userId, e);
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
        return headers;
    }
    
    private PlatformContent convertToPlatformContent(Map<String, Object> status) {
        PlatformContent content = new PlatformContent();
        content.setContentId(status.get("id").toString());
        content.setTitle(""); // 微博没有标题
        content.setBody((String) status.get("text"));
        
        String statusId = status.get("id").toString();
        String userId = ((Map<String, Object>) status.get("user")).get("id").toString();
        content.setUrl("https://weibo.com/" + userId + "/" + statusId);
        
        // 发布时间（微博使用时间戳）
        Long createdAt = ((Number) status.get("created_at")).longValue();
        LocalDateTime publishedAt = LocalDateTime.ofEpochSecond(createdAt, 0, ZoneOffset.UTC);
        content.setPublishedAt(publishedAt);
        
        // 判断内容类型
        String picUrl = (String) status.get("thumbnail_pic");
        if (picUrl != null && !picUrl.isEmpty()) {
            content.setContentType(PlatformContent.ContentType.IMAGE);
        } else {
            content.setContentType(PlatformContent.ContentType.TEXT);
        }
        
        // 互动数据（存储在 metadata 中）
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("repostsCount", status.get("reposts_count"));
        metadata.put("commentsCount", status.get("comments_count"));
        metadata.put("attitudesCount", status.get("attitudes_count"));
        content.setMetadata(metadata);
        
        return content;
    }
}
