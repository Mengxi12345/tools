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
 * 掘金平台适配器
 */
@Slf4j
@Component
public class JuejinAdapter implements PlatformAdapter {
    
    private static final String PLATFORM_TYPE = "JUEJIN";
    private static final String API_BASE_URL = "https://api.juejin.cn";
    
    private final RestTemplate restTemplate;
    
    public JuejinAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }
    
    @Override
    public boolean testConnection(Map<String, Object> config) throws BusinessException {
        try {
            String cookie = (String) config.get("cookie");
            if (cookie == null || cookie.isEmpty()) {
                throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "掘金 cookie 未配置");
            }
            
            HttpHeaders headers = createHeaders(cookie);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/user_api/v1/user/get",
                    HttpMethod.GET,
                    entity,
                    Map.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("掘金连接测试失败", e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "掘金连接测试失败: " + e.getMessage());
        }
    }
    
    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        try {
            String cookie = (String) config.get("cookie");
            HttpHeaders headers = createHeaders(cookie);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // 掘金 API: GET /user_api/v1/user/get?user_id={userId}
            ResponseEntity<Map> response = restTemplate.exchange(
                    API_BASE_URL + "/user_api/v1/user/get?user_id=" + userId,
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
            user.setUserId(data.get("user_id").toString());
            user.setUsername((String) data.get("user_name"));
            user.setDisplayName((String) data.get("user_name"));
            user.setAvatarUrl((String) data.get("avatar_large"));
            
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取掘金用户信息失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND, "获取用户信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public FetchResult getUserContents(String userId, Map<String, Object> config, 
                                      LocalDateTime startTime, LocalDateTime endTime, 
                                      String cursor, Integer limit) throws BusinessException {
        try {
            String cookie = (String) config.get("cookie");
            HttpHeaders headers = createHeaders(cookie);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // 掘金 API: GET /content_api/v1/article/query_list
            String url = API_BASE_URL + "/content_api/v1/article/query_list";
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("user_id", userId);
            requestBody.put("sort_type", 2); // 按时间排序
            if (limit != null && limit > 0) {
                requestBody.put("limit", Math.min(limit, 20));
            }
            if (cursor != null && !cursor.isEmpty()) {
                requestBody.put("cursor", cursor);
            }
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) response.getBody()).get("data");
            if (data == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<Map<String, Object>> articles = (List<Map<String, Object>>) data.get("data");
            if (articles == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<PlatformContent> contents = new ArrayList<>();
            String nextCursor = data.get("cursor") != null ? data.get("cursor").toString() : null;
            boolean hasMore = (Boolean) data.getOrDefault("has_more", false);
            
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
                    log.warn("转换掘金文章失败", e);
                }
            }
            
            return FetchResult.builder()
                    .contents(contents)
                    .nextCursor(nextCursor)
                    .hasMore(hasMore)
                    .build();
        } catch (Exception e) {
            log.error("获取掘金用户内容失败: userId={}", userId, e);
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
    
    private HttpHeaders createHeaders(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Cookie", cookie);
        headers.set("User-Agent", "ContentAggregator/1.0");
        headers.set("Content-Type", "application/json");
        return headers;
    }
    
    private PlatformContent convertToPlatformContent(Map<String, Object> article) {
        PlatformContent content = new PlatformContent();
        content.setContentId(article.get("article_id").toString());
        content.setTitle((String) article.get("title"));
        content.setBody((String) article.get("brief_content"));
        
        String url = (String) article.get("article_url");
        content.setUrl(url != null ? url : "");
        
        // 发布时间（掘金使用时间戳）
        Long ctime = ((Number) article.get("ctime")).longValue();
        LocalDateTime publishedAt = LocalDateTime.ofEpochSecond(ctime, 0, ZoneOffset.UTC);
        content.setPublishedAt(publishedAt);
        
        content.setContentType(PlatformContent.ContentType.TEXT);
        
        // 标签（存储在 metadata 中）
        Map<String, Object> metadata = new HashMap<>();
        List<String> tags = (List<String>) article.get("tags");
        if (tags != null) {
            metadata.put("tags", tags);
        }
        content.setMetadata(metadata);
        
        return content;
    }
}
