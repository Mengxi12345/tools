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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

/**
 * 知识星球 (ZSXQ) 平台适配器。
 * 支持按星球（圈子）拉取「圈主/嘉宾」发布的主题（by_owner），如「刘少小酒馆-刘少专属」。
 *
 * <p>配置项：
 * <ul>
 *   <li>accessToken：Cookie 中的 zsxq_access_token</li>
 *   <li>groupId：星球 ID，如 88885511211582</li>
 * </ul>
 *
 * <p>文章来源：GET /v2/groups/{groupId}/topics?scope=by_owner&count=20
 * <ul>
 *   <li>头像：talk.owner.avatar_url</li>
 *   <li>昵称：talk.owner.alias 或 talk.owner.name</li>
 *   <li>正文：talk.text</li>
 *   <li>图片：talk.images[].original.url（或 large.url）</li>
 *   <li>附件：talk.files[].file_id，下载需请求 GET /v2/files/{file_id}/download_records</li>
 * </ul>
 */
@Slf4j
@Component
public class ZsxqAdapter implements PlatformAdapter {

    private static final String PLATFORM_TYPE = "ZSXQ";
    private static final String API_BASE = "https://api.zsxq.com/v2";

    private static final DateTimeFormatter ZSXQ_TIME = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
        .appendPattern("xx")
        .toFormatter();

    private final RestTemplate restTemplate;

    public ZsxqAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public boolean testConnection(Map<String, Object> config) throws BusinessException {
        String groupId = getGroupId(config, null);
        if (groupId == null || groupId.isEmpty()) {
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "知识星球 groupId 未配置");
        }
        String url = API_BASE + "/groups/" + groupId;
        try {
            log.info("知识星球 请求: GET {}", url);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders(config));
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            log.info("知识星球 响应: GET {} -> status={}, succeeded={}", url, response.getStatusCode(), body != null ? body.get("succeeded") : null);
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                return false;
            }
            return Boolean.TRUE.equals(body.get("succeeded"));
        } catch (Exception e) {
            log.warn("知识星球连接测试失败: groupId={}", groupId, e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "连接测试失败: " + e.getMessage());
        }
    }

    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        String token = getAccessToken(config);
        String url = API_BASE + "/users/" + userId;
        try {
            log.info("知识星球 请求: GET {}", url);
            HttpEntity<String> entity = new HttpEntity<>(createHeaders(config));
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map<String, Object> body = response.getBody();
            log.info("知识星球 响应: GET {} -> status={}, succeeded={}", url, response.getStatusCode(), body != null ? body.get("succeeded") : null);
            if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);
            }
            if (!Boolean.TRUE.equals(body.get("succeeded"))) {
                throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> respData = (Map<String, Object>) body.get("resp_data");
            if (respData == null) throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);
            @SuppressWarnings("unchecked")
            Map<String, Object> user = (Map<String, Object>) respData.get("user");
            if (user == null) throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND);

            PlatformUser pu = new PlatformUser();
            pu.setUserId(getStr(user, "user_id"));
            pu.setUsername(getStr(user, "name"));
            pu.setDisplayName(getStr(user, "name"));
            pu.setAvatarUrl(getStr(user, "avatar_url"));
            return pu;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取知识星球用户信息失败: userId={}", userId, e);
            throw new BusinessException(ErrorCode.PLATFORM_USER_NOT_FOUND, "获取用户信息失败: " + e.getMessage());
        }
    }

    @Override
    public FetchResult getUserContents(String userId, Map<String, Object> config,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       String cursor, Integer limit) throws BusinessException {
        String groupId = getGroupId(config, userId);
        if (groupId == null || groupId.isEmpty()) {
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "知识星球 groupId 未配置");
        }
        int count = 20; // 知识星球每次拉取固定 20 条
        String url;
        URI uri;
        if (cursor != null && !cursor.isEmpty()) {
            // end_time 必须按 query 编码：: -> %3A, + -> %2B，否则服务端会收到「+」被解析为空格而报「无效的 end_time」
            String encodedEndTime = URLEncoder.encode(cursor, StandardCharsets.UTF_8);
            url = API_BASE + "/groups/" + groupId + "/topics?scope=by_owner&count=" + count + "&end_time=" + encodedEndTime;
            uri = URI.create(url);
        } else {
            uri = UriComponentsBuilder.fromHttpUrl(API_BASE + "/groups/" + groupId + "/topics")
                    .queryParam("scope", "by_owner")
                    .queryParam("count", count)
                    .build()
                    .toUri();
            url = uri.toString();
        }
        final int maxRetries = 50;
        final int delaySecMin = 10;
        final int delaySecMax = 18;
        try {
            for (int attempt = 1; attempt <= maxRetries; attempt++) {
                log.info("知识星球 请求: GET {} (groupId={}, count={}, 第{}/{}次)", url, groupId, count, attempt, maxRetries);
                HttpEntity<String> entity = new HttpEntity<>(createHeaders(config));
                ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, entity, Map.class);
                Map<String, Object> body = response.getBody();
                if (!response.getStatusCode().is2xxSuccessful() || body == null) {
                    log.warn("知识星球 响应: GET {} -> status={}, body=null", url, response.getStatusCode());
                    if (attempt < maxRetries) {
                        randomSleep(delaySecMin, delaySecMax);
                        continue;
                    }
                    return FetchResult.builder().contents(Collections.emptyList()).hasMore(false).build();
                }
                if (!Boolean.TRUE.equals(body.get("succeeded"))) {
                    Object code = body.get("code");
                    Object err = body.get("error");
                    log.warn("知识星球 响应: GET {} -> succeeded=false, code={}, error={}, 完整 body={}", url, code, err, body);
                    if (attempt < maxRetries) {
                        randomSleep(delaySecMin, delaySecMax);
                        continue;
                    }
                    throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED,
                        "知识星球返回异常，重试 " + maxRetries + " 次后仍失败: code=" + code + ", error=" + err);
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> respData = (Map<String, Object>) body.get("resp_data");
                if (respData == null) {
                    log.warn("知识星球 响应: GET {} -> resp_data=null", url);
                    if (attempt < maxRetries) {
                        randomSleep(delaySecMin, delaySecMax);
                        continue;
                    }
                    return FetchResult.builder().contents(Collections.emptyList()).hasMore(false).build();
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> topics = (List<Map<String, Object>>) respData.get("topics");
                if (topics == null) {
                    log.warn("知识星球 响应: GET {} -> topics=null", url);
                    if (attempt < maxRetries) {
                        randomSleep(delaySecMin, delaySecMax);
                        continue;
                    }
                    return FetchResult.builder().contents(Collections.emptyList()).hasMore(false).build();
                }

                List<PlatformContent> contents = new ArrayList<>();
                for (Map<String, Object> topic : topics) {
                    if (!"talk".equals(topic.get("type"))) continue;
                    PlatformContent content = mapTalkTopicToContent(topic);
                    if (content == null) continue;
                    if (startTime != null && content.getPublishedAt() != null && content.getPublishedAt().isBefore(startTime)) {
                        continue;
                    }
                    if (endTime != null && content.getPublishedAt() != null && content.getPublishedAt().isAfter(endTime)) {
                        continue;
                    }
                    contents.add(content);
                }
                // 下一页游标：使用本页最后一条（时间最旧）的 create_time，与 API end_time 格式一致并需 URL 编码
                String nextCursor = null;
                if (!topics.isEmpty()) {
                    Object lastCreateTime = topics.get(topics.size() - 1).get("create_time");
                    if (lastCreateTime != null) {
                        nextCursor = lastCreateTime.toString();
                    }
                }
                boolean hasMore = topics.size() >= count;
                // 增量拉取：若本页最旧一条早于 startTime，说明已越过「新内容」时间窗，不再请求更早的页
                if (startTime != null && hasMore && !topics.isEmpty()) {
                    LocalDateTime lastTopicTime = parseCreateTimeToLocal(topics.get(topics.size() - 1).get("create_time"));
                    if (lastTopicTime != null && lastTopicTime.isBefore(startTime)) {
                        hasMore = false;
                        log.info("知识星球 增量拉取: 本页最旧时间 {} 早于 startTime {}，停止翻页", lastTopicTime, startTime);
                    }
                }
                log.info("知识星球 响应: GET {} -> status={}, topics.size={}, 解析条数={}, hasMore={}", url, response.getStatusCode(), topics.size(), contents.size(), hasMore);
                return FetchResult.builder()
                    .contents(contents)
                    .hasMore(hasMore)
                    .nextCursor(hasMore ? nextCursor : null)
                    .fetchedCount(contents.size())
                    .build();
            }
            return FetchResult.builder().contents(Collections.emptyList()).hasMore(false).build();
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("知识星球拉取被中断: groupId={}", groupId, e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "获取内容被中断: " + e.getMessage());
        } catch (Exception e) {
            log.error("获取知识星球主题列表失败: groupId={}", groupId, e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "获取内容失败: " + e.getMessage());
        }
    }

    private static void randomSleep(int secMin, int secMax) throws InterruptedException {
        int sec = secMin + new Random().nextInt(Math.max(1, secMax - secMin + 1));
        Thread.sleep(sec * 1000L);
    }

    @Override
    public boolean validateUserId(String userId, Map<String, Object> config) throws BusinessException {
        try {
            String groupId = getGroupId(config, userId);
            if (groupId != null && !groupId.isEmpty()) {
                testConnection(config);
                return true;
            }
            getUserInfo(userId, config);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 解析 type=talk 的主题为 PlatformContent。
     * 正文：talk.text；元数据保存完整 talk；图片按 image_id 去重，优先 thumbnail.url；附件 file_id 用于后续下载。
     */
    public static PlatformContent mapTalkTopicToContent(Map<String, Object> topic) {
        Object topicIdObj = topic.get("topic_id");
        String contentId = topicIdObj != null ? topicIdObj.toString() : null;
        String title = getStr(topic, "title");
        if (contentId == null) return null;

        @SuppressWarnings("unchecked")
        Map<String, Object> talk = (Map<String, Object>) topic.get("talk");
        if (talk == null) return null;

        // 正文为 talk.text
        String body = getStr(talk, "text");
        List<String> mediaUrls = new ArrayList<>();
        List<Object> fileIds = new ArrayList<>();
        Set<String> seenImageIds = new HashSet<>();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> images = (List<Map<String, Object>>) talk.get("images");
        if (images != null) {
            for (Map<String, Object> img : images) {
                Object imageIdObj = img.get("image_id");
                String imageId = imageIdObj != null ? imageIdObj.toString() : null;
                if (imageId != null && !seenImageIds.add(imageId)) continue; // 按 image_id 去重
                String imgUrl = null;
                Map<String, Object> thumbnail = (Map<String, Object>) img.get("thumbnail");
                if (thumbnail != null && thumbnail.get("url") != null) {
                    imgUrl = thumbnail.get("url").toString();
                }
                if (imgUrl == null) {
                    Map<String, Object> original = (Map<String, Object>) img.get("original");
                    if (original != null && original.get("url") != null) imgUrl = original.get("url").toString();
                }
                if (imgUrl == null) {
                    Map<String, Object> large = (Map<String, Object>) img.get("large");
                    if (large != null && large.get("url") != null) imgUrl = large.get("url").toString();
                }
                if (imgUrl != null) mediaUrls.add(imgUrl);
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> files = (List<Map<String, Object>>) talk.get("files");
        if (files != null) {
            for (Map<String, Object> f : files) {
                Object fid = f.get("file_id");
                if (fid != null) fileIds.add(fid);
            }
        }

        String authorId = null;
        String authorName = null;
        String authorAvatar = null;
        @SuppressWarnings("unchecked")
        Map<String, Object> owner = (Map<String, Object>) talk.get("owner");
        if (owner != null) {
            Object uid = owner.get("user_id");
            authorId = uid != null ? uid.toString() : null;
            authorName = getStr(owner, "alias");
            if (authorName == null || authorName.isEmpty()) authorName = getStr(owner, "name");
            authorAvatar = getStr(owner, "avatar_url");
        }

        LocalDateTime publishedAt = null;
        Object ct = topic.get("create_time");
        if (ct != null) {
            String s = ct.toString();
            try {
                if (s.matches(".*[+-]\\d{2}:\\d{2}$")) {
                    publishedAt = OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
                } else {
                    publishedAt = OffsetDateTime.parse(s, ZSXQ_TIME).toLocalDateTime();
                }
            } catch (Exception ignored) { }
        }

        Map<String, Object> metadata = new HashMap<>();
        putInt(metadata, topic, "likes_count");
        putInt(metadata, topic, "comments_count");
        putInt(metadata, topic, "readers_count");
        putInt(metadata, topic, "rewards_count");
        if (authorAvatar != null) {
            metadata.put("author_avatar_url", authorAvatar);
            metadata.put("userAvatar", authorAvatar); // 仪表盘等前端按 userAvatar 展示头像
        }
        if (!fileIds.isEmpty()) metadata.put("file_ids", fileIds);
        metadata.put("talk", talk);

        Object groupIdObj = null;
        @SuppressWarnings("unchecked")
        Map<String, Object> group = (Map<String, Object>) topic.get("group");
        if (group != null) groupIdObj = group.get("group_id");
        String url = (groupIdObj != null && contentId != null)
            ? "https://wx.zsxq.com/dweb2/index/topic_detail/" + contentId + "?group_id=" + groupIdObj
            : null;

        PlatformContent.ContentType contentType = !mediaUrls.isEmpty()
            ? (body != null && !body.isEmpty() ? PlatformContent.ContentType.TEXT : PlatformContent.ContentType.IMAGE)
            : (!fileIds.isEmpty() ? PlatformContent.ContentType.LINK : PlatformContent.ContentType.TEXT);

        return PlatformContent.builder()
            .contentId(contentId)
            .title(title != null ? title : "")
            .body(body != null ? body : "")
            .url(url)
            .contentType(contentType)
            .mediaUrls(mediaUrls.isEmpty() ? null : mediaUrls)
            .publishedAt(publishedAt)
            .metadata(metadata)
            .authorId(authorId)
            .authorUsername(authorName)
            .build();
    }

    /** 解析知识星球 create_time 字符串为 LocalDateTime，解析失败返回 null */
    private static LocalDateTime parseCreateTimeToLocal(Object ct) {
        if (ct == null) return null;
        String s = ct.toString();
        try {
            if (s.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
            }
            return OffsetDateTime.parse(s, ZSXQ_TIME).toLocalDateTime();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static void putInt(Map<String, Object> out, Map<String, Object> in, String key) {
        Object v = in.get(key);
        if (v instanceof Number) out.put(key, ((Number) v).intValue());
    }

    private HttpHeaders createHeaders(Map<String, Object> config) {
        String token = getAccessToken(config);
        long ts = System.currentTimeMillis() / 1000;
        String requestId = UUID.randomUUID().toString().replace("-", "");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json, text/plain, */*");
        headers.set("Accept-Language", "zh-CN,zh;q=0.9");
        headers.set("Origin", "https://wx.zsxq.com");
        headers.set("Referer", "https://wx.zsxq.com/");
        headers.set("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36");
        headers.set("Priority", "u=1, i");
        headers.set("sec-ch-ua", "\"Not(A:Brand\";v=\"8\", \"Chromium\";v=\"144\", \"Google Chrome\";v=\"144\"");
        headers.set("sec-ch-ua-mobile", "?0");
        headers.set("sec-ch-ua-platform", "\"macOS\"");
        headers.set("sec-fetch-dest", "empty");
        headers.set("sec-fetch-mode", "cors");
        headers.set("sec-fetch-site", "same-site");
        headers.set("x-version", "2.88.0");
        headers.set("x-timestamp", String.valueOf(ts));
        headers.set("x-request-id", requestId);
        String signature = computeSignature(ts, token);
        if (signature != null) {
            headers.set("x-signature", signature);
        }
        headers.set("Cookie", "zsxq_access_token=" + token);
        return headers;
    }

    /** 知识星球 API 可能校验的签名：SHA-1(timestamp + token) 小写十六进制；若仍返回 1059 可尝试从浏览器复制 x-signature 或关闭校验。 */
    private static String computeSignature(long timestamp, String token) {
        if (token == null || token.isEmpty()) return null;
        try {
            String input = timestamp + token;
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String getAccessToken(Map<String, Object> config) {
        if (config == null) return "";
        Object t = config.get("accessToken");
        if (t == null) t = config.get("zsxq_access_token");
        return t != null ? t.toString() : "";
    }

    private static String getGroupId(Map<String, Object> config, String userIdOverride) {
        if (config != null) {
            Object g = config.get("groupId");
            if (g != null && !g.toString().isEmpty()) return g.toString();
        }
        return userIdOverride;
    }
}
