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
 * CSDN 平台适配器
 */
@Slf4j
@Component
public class CSDNAdapter implements PlatformAdapter {
    
    private static final String PLATFORM_TYPE = "CSDN";
    private static final String API_BASE_URL = "https://blog.csdn.net";
    
    private final RestTemplate restTemplate;
    
    public CSDNAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }
    
    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }
    
    @Override
    public boolean testConnection(Map<String, Object> config) throws BusinessException {
        try {
            // CSDN API 通常不需要特殊认证，这里简化处理
            return true;
        } catch (Exception e) {
            log.error("CSDN 连接测试失败", e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "CSDN 连接测试失败: " + e.getMessage());
        }
    }
    
    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // CSDN API: GET /community/user-api/v1/user/get?username={userId}
            ResponseEntity<Map> response = restTemplate.exchange(
                    "https://blog.csdn.net/community/user-api/v1/user/get?username=" + userId,
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
            user.setUserId(data.get("username").toString());
            user.setUsername((String) data.get("username"));
            user.setDisplayName((String) data.get("nickname"));
            user.setAvatarUrl((String) data.get("avatar"));
            
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 CSDN 用户信息失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND, "获取用户信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public FetchResult getUserContents(String userId, Map<String, Object> config, 
                                      LocalDateTime startTime, LocalDateTime endTime, 
                                      String cursor, Integer limit) throws BusinessException {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            // CSDN API: GET /community/user-api/v1/user/getArticleList
            String url = "https://blog.csdn.net/community/user-api/v1/user/getArticleList";
            url += "?username=" + userId;
            if (limit != null && limit > 0) {
                url += "&pageSize=" + Math.min(limit, 20);
            }
            if (cursor != null && !cursor.isEmpty()) {
                url += "&pageNo=" + cursor;
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
            
            Map<String, Object> data = (Map<String, Object>) ((Map<String, Object>) response.getBody()).get("data");
            if (data == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<Map<String, Object>> articles = (List<Map<String, Object>>) data.get("list");
            if (articles == null) {
                return FetchResult.builder()
                        .contents(new ArrayList<>())
                        .hasMore(false)
                        .build();
            }
            
            List<PlatformContent> contents = new ArrayList<>();
            Integer currentPage = (Integer) data.get("pageNo");
            Integer totalPages = (Integer) data.get("totalPage");
            String nextCursor = (currentPage != null && totalPages != null && currentPage < totalPages) 
                    ? String.valueOf(currentPage + 1) : null;
            
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
                    log.warn("转换 CSDN 文章失败", e);
                }
            }
            
            return FetchResult.builder()
                    .contents(contents)
                    .nextCursor(nextCursor)
                    .hasMore(nextCursor != null)
                    .build();
        } catch (Exception e) {
            log.error("获取 CSDN 用户内容失败: userId={}", userId, e);
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
    
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "ContentAggregator/1.0");
        headers.set("Accept", "application/json");
        return headers;
    }
    
    private PlatformContent convertToPlatformContent(Map<String, Object> article) {
        PlatformContent content = new PlatformContent();
        content.setContentId(article.get("articleId").toString());
        content.setTitle((String) article.get("title"));
        content.setBody((String) article.get("description"));
        
        String url = (String) article.get("articleUrl");
        content.setUrl(url != null ? url : "");
        
        // 发布时间
        String publishTime = (String) article.get("publishTime");
        if (publishTime != null) {
            try {
                LocalDateTime publishedAt = LocalDateTime.parse(
                        publishTime.substring(0, 19),
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
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
        Map<String, Object> metadata = new HashMap<>();
        List<String> tags = (List<String>) article.get("tags");
        if (tags != null) {
            metadata.put("tags", tags);
        }
        content.setMetadata(metadata);
        
        return content;
    }
}
