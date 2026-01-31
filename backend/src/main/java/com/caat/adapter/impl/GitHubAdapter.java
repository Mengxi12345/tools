package com.caat.adapter.impl;

import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.FetchResult;
import com.caat.adapter.model.PlatformContent;
import com.caat.adapter.model.PlatformUser;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GitHub 平台适配器
 */
@Slf4j
@Component
public class GitHubAdapter implements PlatformAdapter {
    
    private static final String PLATFORM_TYPE = "github";
    private static final String GITHUB_API_BASE = "https://api.github.com";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public GitHubAdapter(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }
    
    @Override
    public boolean testConnection(Map<String, Object> config) throws BusinessException {
        try {
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                GITHUB_API_BASE + "/user",
                HttpMethod.GET,
                entity,
                String.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("GitHub 连接测试失败", e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "GitHub API 连接失败: " + e.getMessage());
        }
    }
    
    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        try {
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String url = GITHUB_API_BASE + "/users/" + userId;
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND, "GitHub 用户不存在: " + userId);
            }
            
            JsonNode userNode = objectMapper.readTree(response.getBody());
            
            PlatformUser user = new PlatformUser();
            user.setUserId(String.valueOf(userNode.get("id").asLong()));
            user.setUsername(userNode.get("login").asText());
            user.setDisplayName(userNode.has("name") && !userNode.get("name").isNull() 
                ? userNode.get("name").asText() 
                : userNode.get("login").asText());
            user.setAvatarUrl(userNode.has("avatar_url") ? userNode.get("avatar_url").asText() : null);
            user.setBio(userNode.has("bio") && !userNode.get("bio").isNull() ? userNode.get("bio").asText() : null);
            user.setProfileUrl(userNode.has("html_url") ? userNode.get("html_url").asText() : null);
            
            return user;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 GitHub 用户信息失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.PLATFORM_API_ERROR, "获取 GitHub 用户信息失败: " + e.getMessage());
        }
    }
    
    @Override
    public FetchResult getUserContents(
        String userId,
        Map<String, Object> config,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String cursor,
        Integer limit
    ) throws BusinessException {
        try {
            List<PlatformContent> contents = new ArrayList<>();
            String nextCursor = null;
            
            // 获取用户的事件（Issues, PRs, Commits 等）
            contents.addAll(fetchUserEvents(userId, config, startTime, endTime, limit));
            
            // 获取用户的公开仓库
            contents.addAll(fetchUserRepositories(userId, config, startTime, endTime, limit));
            
            FetchResult result = new FetchResult();
            result.setContents(contents);
            result.setNextCursor(nextCursor);
            result.setHasMore(false); // GitHub API 使用分页，这里简化处理
            
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 GitHub 用户内容失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.PLATFORM_API_ERROR, "获取 GitHub 用户内容失败: " + e.getMessage());
        }
    }
    
    @Override
    public boolean validateUserId(String userId, Map<String, Object> config) throws BusinessException {
        try {
            PlatformUser user = getUserInfo(userId, config);
            return user != null;
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.PLATFORM_USER_NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }
    
    /**
     * 获取用户事件（Issues, PRs 等）
     */
    private List<PlatformContent> fetchUserEvents(
        String userId,
        Map<String, Object> config,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer limit
    ) {
        List<PlatformContent> contents = new ArrayList<>();
        
        try {
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String url = GITHUB_API_BASE + "/users/" + userId + "/events/public";
            if (limit != null && limit > 0) {
                url += "?per_page=" + Math.min(limit, 100); // GitHub 限制每页最多 100
            }
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode eventsNode = objectMapper.readTree(response.getBody());
                
                if (eventsNode.isArray()) {
                    for (JsonNode eventNode : eventsNode) {
                        PlatformContent content = convertEventToContent(eventNode, userId);
                        if (content != null && isInTimeRange(content.getPublishedAt(), startTime, endTime)) {
                            contents.add(content);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取 GitHub 用户事件失败: userId={}", userId, e);
        }
        
        return contents;
    }
    
    /**
     * 获取用户仓库
     */
    private List<PlatformContent> fetchUserRepositories(
        String userId,
        Map<String, Object> config,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer limit
    ) {
        List<PlatformContent> contents = new ArrayList<>();
        
        try {
            HttpHeaders headers = createHeaders(config);
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            String url = GITHUB_API_BASE + "/users/" + userId + "/repos?sort=updated";
            if (limit != null && limit > 0) {
                url += "&per_page=" + Math.min(limit, 100);
            }
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode reposNode = objectMapper.readTree(response.getBody());
                
                if (reposNode.isArray()) {
                    for (JsonNode repoNode : reposNode) {
                        PlatformContent content = convertRepoToContent(repoNode, userId);
                        if (content != null && isInTimeRange(content.getPublishedAt(), startTime, endTime)) {
                            contents.add(content);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取 GitHub 用户仓库失败: userId={}", userId, e);
        }
        
        return contents;
    }
    
    /**
     * 将 GitHub 事件转换为 PlatformContent
     */
    private PlatformContent convertEventToContent(JsonNode eventNode, String userId) {
        try {
            String type = eventNode.get("type").asText();
            JsonNode payload = eventNode.get("payload");
            JsonNode repo = eventNode.get("repo");
            
            PlatformContent content = new PlatformContent();
            content.setContentId(eventNode.get("id").asText());
            content.setContentType(PlatformContent.ContentType.TEXT);
            
            // 根据事件类型设置标题和内容
            String title = type;
            String body = "";
            
            if ("IssuesEvent".equals(type) && payload.has("issue")) {
                JsonNode issue = payload.get("issue");
                title = "Issue: " + issue.get("title").asText();
                body = issue.has("body") && !issue.get("body").isNull() 
                    ? issue.get("body").asText() 
                    : "";
                content.setUrl(issue.get("html_url").asText());
            } else if ("PullRequestEvent".equals(type) && payload.has("pull_request")) {
                JsonNode pr = payload.get("pull_request");
                title = "PR: " + pr.get("title").asText();
                body = pr.has("body") && !pr.get("body").isNull() 
                    ? pr.get("body").asText() 
                    : "";
                content.setUrl(pr.get("html_url").asText());
            } else if ("PushEvent".equals(type) && payload.has("commits")) {
                JsonNode commits = payload.get("commits");
                if (commits.isArray() && commits.size() > 0) {
                    JsonNode commit = commits.get(0);
                    title = "Push: " + commit.get("message").asText();
                    body = commit.has("message") ? commit.get("message").asText() : "";
                    content.setUrl(repo.get("url").asText().replace("api.github.com/repos", "github.com"));
                }
            } else {
                // 其他类型的事件，使用默认格式
                title = type + " in " + (repo != null ? repo.get("name").asText() : "");
                content.setUrl(repo != null && repo.has("url") 
                    ? repo.get("url").asText().replace("api.github.com/repos", "github.com") 
                    : "");
            }
            
            content.setTitle(title);
            content.setBody(body);
            
            // 解析发布时间
            if (eventNode.has("created_at")) {
                String createdAtStr = eventNode.get("created_at").asText();
                LocalDateTime publishedAt = ZonedDateTime.parse(createdAtStr, DateTimeFormatter.ISO_DATE_TIME)
                    .toLocalDateTime();
                content.setPublishedAt(publishedAt);
            } else {
                content.setPublishedAt(LocalDateTime.now());
            }
            
            // 设置元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", type);
            metadata.put("actor", eventNode.has("actor") ? eventNode.get("actor").get("login").asText() : userId);
            if (repo != null) {
                metadata.put("repo", repo.get("name").asText());
            }
            content.setMetadata(metadata);
            
            return content;
        } catch (Exception e) {
            log.warn("转换 GitHub 事件失败", e);
            return null;
        }
    }
    
    /**
     * 将 GitHub 仓库转换为 PlatformContent
     */
    private PlatformContent convertRepoToContent(JsonNode repoNode, String userId) {
        try {
            PlatformContent content = new PlatformContent();
            content.setContentId(repoNode.get("id").asText());
            content.setContentType(PlatformContent.ContentType.LINK);
            content.setTitle("Repository: " + repoNode.get("name").asText());
            content.setBody(repoNode.has("description") && !repoNode.get("description").isNull() 
                ? repoNode.get("description").asText() 
                : "");
            content.setUrl(repoNode.get("html_url").asText());
            
            // 解析更新时间
            if (repoNode.has("updated_at")) {
                String updatedAtStr = repoNode.get("updated_at").asText();
                LocalDateTime publishedAt = ZonedDateTime.parse(updatedAtStr, DateTimeFormatter.ISO_DATE_TIME)
                    .toLocalDateTime();
                content.setPublishedAt(publishedAt);
            } else {
                content.setPublishedAt(LocalDateTime.now());
            }
            
            // 设置元数据
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "repository");
            metadata.put("language", repoNode.has("language") && !repoNode.get("language").isNull() 
                ? repoNode.get("language").asText() 
                : null);
            metadata.put("stars", repoNode.has("stargazers_count") ? repoNode.get("stargazers_count").asInt() : 0);
            metadata.put("forks", repoNode.has("forks_count") ? repoNode.get("forks_count").asInt() : 0);
            content.setMetadata(metadata);
            
            return content;
        } catch (Exception e) {
            log.warn("转换 GitHub 仓库失败", e);
            return null;
        }
    }
    
    /**
     * 创建 HTTP 请求头（包含认证信息）
     */
    private HttpHeaders createHeaders(Map<String, Object> config) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "Content-Aggregator-Tool");
        
        // 添加认证信息
        if (config != null && config.containsKey("apiKey")) {
            String apiKey = String.valueOf(config.get("apiKey"));
            headers.set("Authorization", "token " + apiKey);
        }
        
        return headers;
    }
    
    /**
     * 检查时间是否在范围内
     */
    private boolean isInTimeRange(LocalDateTime time, LocalDateTime startTime, LocalDateTime endTime) {
        if (time == null) {
            return false;
        }
        
        if (startTime != null && time.isBefore(startTime)) {
            return false;
        }
        
        if (endTime != null && time.isAfter(endTime)) {
            return false;
        }
        
        return true;
    }
}
