package com.caat.adapter;

import com.caat.adapter.impl.ZsxqAdapter;
import com.caat.adapter.model.PlatformContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知识星球 (ZSXQ) API 解析与拉取测试。
 * 用于验证从 api.zsxq.com 获取的用户足迹（文章）响应的解析逻辑。
 *
 * API 示例：
 * - 用户信息: GET https://api.zsxq.com/v2/users/{userId}
 * - 用户足迹(文章): GET https://api.zsxq.com/v2/users/{userId}/footprints?count=20&group_id={groupId}&filter=group&filter_group_id={groupId}
 * - 星球主题(圈主): GET https://api.zsxq.com/v2/groups/{groupId}/topics?scope=by_owner&count=20
 * - 附件下载记录: GET https://api.zsxq.com/v2/files/{file_id}/download_records?count=48
 */
class ZsxqApiParseTest {

    private static final String API_BASE = "https://api.zsxq.com/v2";
    // 知识星球返回如 2026-01-31T19:46:48.131+0800（时区无冒号）
    private static final DateTimeFormatter ZSXQ_TIME = new DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
        .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
        .appendPattern("xx")
        .toFormatter();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 使用样本 JSON 测试解析：将 ZSXQ 足迹响应解析为 PlatformContent 列表。
     */
    @Test
    void testParseFootprintsResponse() throws Exception {
        ClassPathResource resource = new ClassPathResource("zsxq-footprints-sample.json");
        String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        assertTrue(Boolean.TRUE.equals(root.get("succeeded")));

        @SuppressWarnings("unchecked")
        Map<String, Object> respData = (Map<String, Object>) root.get("resp_data");
        assertNotNull(respData);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> footprints = (List<Map<String, Object>>) respData.get("footprints");
        assertNotNull(footprints);
        assertFalse(footprints.isEmpty());

        List<PlatformContent> contents = parseFootprintsToContents(footprints);

        assertEquals(2, contents.size());

        // 第一条：仅图片
        PlatformContent first = contents.get(0);
        assertEquals("55188555814412154", first.getContentId());
        assertEquals("【图片】", first.getTitle());
        assertTrue(first.getBody() == null || first.getBody().isEmpty());
        assertEquals(PlatformContent.ContentType.IMAGE, first.getContentType());
        assertNotNull(first.getMediaUrls());
        assertTrue(first.getMediaUrls().stream().anyMatch(u -> u.contains("original.jpg")));
        assertTrue(first.getPublishedAt().getYear() == 2026 && first.getPublishedAt().getMonthValue() == 1
            && first.getPublishedAt().getDayOfMonth() == 31 && first.getPublishedAt().getHour() == 19
            && first.getPublishedAt().getMinute() == 46);
        assertEquals("844151512144842", first.getAuthorId());
        assertEquals("艾小米", first.getAuthorUsername());
        assertEquals(6, first.getMetadata().get("likes_count"));
        assertEquals(192, first.getMetadata().get("readers_count"));

        // 第二条：文本
        PlatformContent second = contents.get(1);
        assertEquals("22811228552481121", second.getContentId());
        assertTrue(second.getTitle().startsWith("第一条还没看"));
        assertTrue(second.getBody().contains("上升到被jin yan的高度"));
        assertEquals(PlatformContent.ContentType.TEXT, second.getContentType());
        assertTrue(second.getPublishedAt().getYear() == 2026 && second.getPublishedAt().getMonthValue() == 1
            && second.getPublishedAt().getDayOfMonth() == 26 && second.getPublishedAt().getHour() == 14
            && second.getPublishedAt().getMinute() == 10);
        assertEquals(19, second.getMetadata().get("likes_count"));
        assertEquals(17, second.getMetadata().get("comments_count"));
    }

    /**
     * 使用样本 JSON 测试解析：将 ZSXQ 星球主题（by_owner）响应解析为 PlatformContent 列表。
     * 校验 talk：头像 author_avatar_url、昵称 alias、正文 text、图片 url、附件 file_id。
     */
    @Test
    void testParseTopicsByOwnerResponse() throws Exception {
        ClassPathResource resource = new ClassPathResource("zsxq-topics-by-owner-sample.json");
        String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        @SuppressWarnings("unchecked")
        Map<String, Object> root = objectMapper.readValue(json, Map.class);
        assertTrue(Boolean.TRUE.equals(root.get("succeeded")));

        @SuppressWarnings("unchecked")
        Map<String, Object> respData = (Map<String, Object>) root.get("resp_data");
        assertNotNull(respData);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topics = (List<Map<String, Object>>) respData.get("topics");
        assertNotNull(topics);
        assertFalse(topics.isEmpty());

        List<PlatformContent> contents = new ArrayList<>();
        for (Map<String, Object> topic : topics) {
            if (!"talk".equals(topic.get("type"))) continue;
            PlatformContent c = ZsxqAdapter.mapTalkTopicToContent(topic);
            if (c != null) contents.add(c);
        }

        assertEquals(3, contents.size());

        // 第一条：纯文本
        PlatformContent first = contents.get(0);
        assertEquals("82811228214241282", first.getContentId());
        assertTrue(first.getTitle().startsWith("不用愁我"));
        assertTrue(first.getBody().contains("中国好声音"));
        assertEquals("815842152412452", first.getAuthorId());
        assertEquals("刘少本少", first.getAuthorUsername());
        assertTrue(first.getMetadata().get("author_avatar_url").toString().contains("images.zsxq.com"));
        assertEquals(171, first.getMetadata().get("likes_count"));
        assertNull(first.getMediaUrls());

        // 第二条：文本+图片
        PlatformContent second = contents.get(1);
        assertEquals("14588445482245282", second.getContentId());
        assertTrue(second.getBody().contains("啥节目都来邀请我"));
        assertNotNull(second.getMediaUrls());
        assertTrue(second.getMediaUrls().stream().anyMatch(u -> u.contains("original.jpg")));

        // 第三条：含附件 file_id
        PlatformContent third = contents.get(2);
        assertEquals("14588455284282112", third.getContentId());
        assertTrue(third.getTitle().contains("低位篇"));
        @SuppressWarnings("unchecked")
        List<Object> fileIds = (List<Object>) third.getMetadata().get("file_ids");
        assertNotNull(fileIds);
        assertEquals(1, fileIds.size());
        assertEquals(415545848541528L, ((Number) fileIds.get(0)).longValue());
    }

    /**
     * 将 ZSXQ 足迹列表解析为 PlatformContent 列表（与适配器内可复用逻辑一致）。
     */
    public static List<PlatformContent> parseFootprintsToContents(List<Map<String, Object>> footprints) {
        List<PlatformContent> result = new ArrayList<>();
        for (Map<String, Object> fp : footprints) {
            if (!"topic".equals(fp.get("type"))) continue;
            @SuppressWarnings("unchecked")
            Map<String, Object> topic = (Map<String, Object>) fp.get("topic");
            if (topic == null) continue;

            PlatformContent content = mapTopicToContent(topic);
            if (content != null) result.add(content);
        }
        return result;
    }

    private static PlatformContent mapTopicToContent(Map<String, Object> topic) {
        Object topicIdObj = topic.get("topic_id");
        String contentId = topicIdObj != null ? topicIdObj.toString() : null;
        String title = getStr(topic, "title");
        if (contentId == null) return null;

        @SuppressWarnings("unchecked")
        Map<String, Object> solution = (Map<String, Object>) topic.get("solution");
        String body = null;
        List<String> mediaUrls = new ArrayList<>();
        boolean hasImage = false;
        if (solution != null) {
            body = getStr(solution, "text");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> images = (List<Map<String, Object>>) solution.get("images");
            if (images != null && !images.isEmpty()) {
                hasImage = true;
                for (Map<String, Object> img : images) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> original = (Map<String, Object>) img.get("original");
                    if (original != null && original.get("url") != null) {
                        mediaUrls.add(original.get("url").toString());
                    } else {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> large = (Map<String, Object>) img.get("large");
                        if (large != null && large.get("url") != null) {
                            mediaUrls.add(large.get("url").toString());
                        }
                    }
                }
            }
        }

        PlatformContent.ContentType contentType = hasImage && (body == null || body.isEmpty())
            ? PlatformContent.ContentType.IMAGE
            : PlatformContent.ContentType.TEXT;

        LocalDateTime publishedAt = null;
        Object ct = topic.get("create_time");
        if (ct != null) {
            String s = ct.toString();
            try {
                // 兼容 +0800 与 +08:00
                if (s.matches(".*[+-]\\d{2}:\\d{2}$")) {
                    publishedAt = OffsetDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
                } else {
                    publishedAt = OffsetDateTime.parse(s, ZSXQ_TIME).toLocalDateTime();
                }
            } catch (Exception ignored) { }
        }

        String authorId = null;
        String authorName = null;
        if (solution != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> owner = (Map<String, Object>) solution.get("owner");
            if (owner != null) {
                Object uid = owner.get("user_id");
                authorId = uid != null ? uid.toString() : null;
                authorName = getStr(owner, "name");
            }
        }

        Map<String, Object> metadata = new HashMap<>();
        putInt(metadata, topic, "likes_count");
        putInt(metadata, topic, "comments_count");
        putInt(metadata, topic, "readers_count");
        putInt(metadata, topic, "rewards_count");

        Object groupIdObj = null;
        @SuppressWarnings("unchecked")
        Map<String, Object> group = (Map<String, Object>) topic.get("group");
        if (group != null) groupIdObj = group.get("group_id");
        String url = (groupIdObj != null && contentId != null)
            ? "https://wx.zsxq.com/dweb2/index/topic_detail/" + contentId + "?group_id=" + groupIdObj
            : null;

        return PlatformContent.builder()
            .contentId(contentId)
            .title(title)
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

    private static String getStr(Map<String, Object> m, String key) {
        Object v = m.get(key);
        return v != null ? v.toString() : null;
    }

    private static void putInt(Map<String, Object> out, Map<String, Object> in, String key) {
        Object v = in.get(key);
        if (v instanceof Number) out.put(key, ((Number) v).intValue());
    }

    /**
     * 可选：使用真实 Token 请求知识星球 API 并解析用户足迹。
     * 需设置环境变量 ZSXQ_ACCESS_TOKEN；请求需携带 Cookie 与 x-version/x-timestamp/x-signature 等头（若接口校验签名则可能需从浏览器复制完整请求）。
     */
    @Test
    void testFetchUserFootprints_whenTokenProvided() {
        String accessToken = System.getenv("ZSXQ_ACCESS_TOKEN");
        if (accessToken == null || accessToken.isBlank()) {
            // 未配置 token 时跳过，不失败
            return;
        }

        String userId = "844151512144842";
        String groupId = "88885511211582";
        String url = API_BASE + "/users/" + userId + "/footprints?count=20&group_id=" + groupId
            + "&filter=group&filter_group_id=" + groupId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("accept", "application/json, text/plain, */*");
        headers.set("origin", "https://wx.zsxq.com");
        headers.set("referer", "https://wx.zsxq.com/");
        headers.set("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36");
        headers.set("x-version", "2.88.0");
        headers.set("x-timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        headers.set("Cookie", "zsxq_access_token=" + accessToken);

        RestTemplate rest = new RestTemplate();
        HttpEntity<String> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.GET, entity, Map.class);

        assertTrue(response.getStatusCode().is2xxSuccessful(), "API 应返回 2xx");
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(Boolean.TRUE.equals(body.get("succeeded")), "succeeded 应为 true");

        @SuppressWarnings("unchecked")
        Map<String, Object> respData = (Map<String, Object>) body.get("resp_data");
        assertNotNull(respData);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> footprints = (List<Map<String, Object>>) respData.get("footprints");
        assertNotNull(footprints);

        List<PlatformContent> contents = parseFootprintsToContents(footprints);
        assertFalse(contents.isEmpty(), "解析后应至少有一条内容");
        for (PlatformContent c : contents) {
            assertNotNull(c.getContentId());
            assertNotNull(c.getTitle());
            assertNotNull(c.getPublishedAt());
        }
    }
}
