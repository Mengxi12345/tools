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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TimeStore (timestore.vip) 平台适配器
 * 支持通过 mate-auth Token 调用 mymblog 接口拉取并保存文章
 * 由 AdapterRegistrationConfig 显式注册为 Bean，确保被 AdapterFactory 加载
 */
@Slf4j
public class TimeStoreAdapter implements PlatformAdapter {

    private static final String PLATFORM_TYPE = "TIMESTORE";
    /** 真实 API：我的博客列表（api.timestore.vip） */
    private static final String DEFAULT_MYBLOG_PATH = "/timeline/mymblog";
    /** 用户资料：头像、简介（api.timestore.vip） */
    private static final String PROFILE_DETAIL_PATH = "/profile/detail";

    private final RestTemplate restTemplate;

    public TimeStoreAdapter(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public String getPlatformType() {
        return PLATFORM_TYPE;
    }

    @Override
    public boolean testConnection(Map<String, Object> config) throws BusinessException {
        String baseUrl = getBaseUrl(config);
        String token = getMateAuth(config);
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "TimeStore mate-auth 未配置");
        }
        try {
            HttpHeaders headers = createHeaders(normalizeMateAuth(token));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = buildUrl(baseUrl, DEFAULT_MYBLOG_PATH, "current=1&size=1&uid=0&id=0&screen=0");
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("TimeStore 连接测试失败: baseUrl={}", baseUrl, e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED,
                "TimeStore 连接测试失败: " + e.getMessage());
        }
    }

    @Override
    public PlatformUser getUserInfo(String userId, Map<String, Object> config) throws BusinessException {
        // TimeStore 为“我的博客”，无独立用户接口时返回占位用户
        PlatformUser user = new PlatformUser();
        user.setUserId(userId != null && !userId.isEmpty() ? userId : "me");
        user.setUsername("TimeStore");
        user.setDisplayName("TimeStore 博客");
        return user;
    }

    @Override
    public Optional<Map<String, String>> getProfileDetail(String userId, Map<String, Object> config) throws BusinessException {
        String baseUrl = getBaseUrl(config);
        String token = getMateAuth(config);
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "TimeStore mate-auth 未配置");
        }
        String uid = (userId != null && !userId.isEmpty()) ? userId : "0";
        try {
            HttpHeaders headers = createHeaders(normalizeMateAuth(token));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            String url = buildUrl(baseUrl, PROFILE_DETAIL_PATH, "uid=" + uid);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Optional.empty();
            }
            Map<String, Object> body = response.getBody();
            Object dataObj = body.get("data");
            Map<?, ?> data = dataObj instanceof Map ? (Map<?, ?>) dataObj : (body.containsKey("data") ? null : body);
            if (data == null) {
                return Optional.empty();
            }
            String avatar = getStringFromMap(data, "avatar");
            if (avatar == null) avatar = getStringFromMap(data, "userAvatar");
            if (avatar == null) avatar = getStringFromMap(data, "avatarUrl");
            String introduction = getStringFromMap(data, "selfIntroduction");
            if (introduction == null) introduction = getStringFromMap(data, "introduction");
            if (introduction == null) introduction = getStringFromMap(data, "desc");
            if (introduction == null) introduction = getStringFromMap(data, "description");
            if (avatar == null && introduction == null) {
                return Optional.empty();
            }
            Map<String, String> result = new HashMap<>();
            if (avatar != null) result.put("userAvatar", avatar);
            if (introduction != null) result.put("selfIntroduction", introduction);
            log.info("TimeStore 拉取用户资料: uid={}, 有头像={}, 有简介={}", uid, avatar != null, introduction != null);
            return Optional.of(result);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("TimeStore 拉取用户资料失败: uid={}, error={}", uid, e.getMessage());
            return Optional.empty();
        }
    }

    private static String getStringFromMap(Map<?, ?> map, String key) {
        Object v = map == null ? null : map.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 单页拉取：每次调用只请求一页，返回 nextCursor/hasMore 供调用方循环。
     * 每页条数随机 100~150，降低固定请求特征、便于防限流。
     */
    @Override
    public FetchResult getUserContents(String userId, Map<String, Object> config,
                                       LocalDateTime startTime, LocalDateTime endTime,
                                       String cursor, Integer limit) throws BusinessException {
        String baseUrl = getBaseUrl(config);
        String token = getMateAuth(config);
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "TimeStore mate-auth 未配置");
        }
        try {
            HttpHeaders headers = createHeaders(normalizeMateAuth(token));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            // 随机 100~150 条/页，防止固定请求特征被限流
            int pageSize = (limit != null && limit > 0)
                ? Math.min(limit, 150)
                : (100 + ThreadLocalRandom.current().nextInt(51));
            if (pageSize < 100) pageSize = 100 + ThreadLocalRandom.current().nextInt(51);
            // 使用配置的 userId 作为 uid 请求该用户时间线
            String uidFromToken = getUserIdFromMateAuthToken(token);
            String uid = (userId != null && !userId.isEmpty() && !"me".equalsIgnoreCase(userId))
                ? userId
                : ((uidFromToken != null && !uidFromToken.isEmpty()) ? uidFromToken : "0");
            if (uidFromToken != null && !uidFromToken.isEmpty() && !uidFromToken.equals(uid)) {
                log.debug("TimeStore 拉取配置用户内容: uid={}, token 用户={}", uid, uidFromToken);
            }
            int currentPage = 1;
            if (cursor != null && !cursor.isEmpty()) {
                try {
                    currentPage = Integer.parseInt(cursor);
                } catch (NumberFormatException ignored) {}
            }
            StringBuilder q = new StringBuilder()
                .append("current=").append(currentPage)
                .append("&size=").append(pageSize)
                .append("&uid=").append(uid)
                .append("&id=0&screen=0");
            if (startTime != null) {
                q.append("&date=").append(startTime.format(DateTimeFormatter.ISO_LOCAL_DATE));
            }
            String url = buildUrl(baseUrl, DEFAULT_MYBLOG_PATH, q.toString());
            String dateParam = startTime != null ? startTime.format(DateTimeFormatter.ISO_LOCAL_DATE) : null;
            log.info("TimeStore 刷新内容 请求 地址: {}", url);
            log.info("TimeStore 刷新内容 入参: uid={}, current={}, size={}, date={}, mateAuth=Bearer ***", uid, currentPage, pageSize, dateParam);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            logResponse(url, response);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return FetchResult.builder().contents(new ArrayList<>()).nextCursor(null).hasMore(false).fetchedCount(0).build();
            }
            Map<String, Object> body = response.getBody();
            Object dataObj = body != null ? body.get("data") : null;
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> records = extractRecordsList(dataObj, body);
            List<PlatformContent> pageContents = new ArrayList<>();
            if (records != null && !records.isEmpty()) {
                int skippedStart = 0, skippedEnd = 0;
                for (Map<String, Object> record : records) {
                    try {
                        PlatformContent content = mapRecordToPlatformContent(record, "https://web.timestore.vip");
                        if (content.getPublishedAt() != null) {
                            if (startTime != null && content.getPublishedAt().isBefore(startTime)) {
                                skippedStart++;
                                continue;
                            }
                            if (endTime != null && content.getPublishedAt().isAfter(endTime)) {
                                skippedEnd++;
                                continue;
                            }
                        }
                        pageContents.add(content);
                    } catch (Exception e) {
                        log.warn("转换 TimeStore 记录失败: record id={}", record.get("id"), e);
                    }
                }
                if (skippedStart > 0 || skippedEnd > 0) {
                    log.info("[保存排查] TimeStore 本页因时间过滤跳过: skippedStart={}, skippedEnd={}", skippedStart, skippedEnd);
                }
                log.info("TimeStore 本页 records={}, 解析并加入={}", records.size(), pageContents.size());
            } else {
                log.info("TimeStore 响应无 records 或 records 为空");
            }
            Map<String, Object> dataMap = dataObj instanceof Map ? (Map<String, Object>) dataObj : (body != null ? body : new HashMap<>());
            int totalPages = parseInt(dataMap.get("pages"), 1);
            int cur = parseInt(dataMap.get("current"), 1);
            boolean hasMore = cur < totalPages;
            String nextCursor = hasMore ? String.valueOf(cur + 1) : null;
            log.info("TimeStore 本页返回 {} 条, hasMore={}, nextCursor={}", pageContents.size(), hasMore, nextCursor);
            return FetchResult.builder()
                .contents(pageContents)
                .nextCursor(nextCursor)
                .hasMore(hasMore)
                .fetchedCount(pageContents.size())
                .build();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取 TimeStore 内容失败: baseUrl={}", baseUrl, e);
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "获取内容失败: " + e.getMessage());
        }
    }

    /** 刷新内容时打印出参：HTTP 状态、body.code/msg、data.records 条数、body 摘要 */
    private void logResponse(String url, ResponseEntity<Map> response) {
        try {
            int status = response.getStatusCode().value();
            Map<String, Object> body = response.getBody();
            Object code = body != null ? body.get("code") : null;
            Object msg = body != null ? body.get("msg") : null;
            int recordsCount = 0;
            if (body != null) {
                Object data = body.get("data");
                if (data instanceof Map) {
                    Object records = ((Map<?, ?>) data).get("records");
                    Object list = ((Map<?, ?>) data).get("list");
                    if (records instanceof List) recordsCount = ((List<?>) records).size();
                    else if (list instanceof List) recordsCount = ((List<?>) list).size();
                }
            }
            log.info("TimeStore 刷新内容 出参: status={}, code={}, msg={}, data.records.size={}", status, code, msg, recordsCount);
            if (body != null && log.isDebugEnabled()) {
                String bodyStr = body.toString();
                int maxLen = 2000;
                String preview = bodyStr.length() > maxLen ? bodyStr.substring(0, maxLen) + "..." : bodyStr;
                log.debug("TimeStore 刷新内容 出参 body: {}", preview);
            }
        } catch (Exception e) {
            log.warn("TimeStore 刷新内容 出参 打印异常: {}", e.getMessage());
        }
    }

    /** 从 API 的 data 或 body 中取出 records 列表（支持 data 为 Map 含 records/list，或 data 为 List） */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> extractRecordsList(Object dataObj, Map<String, Object> body) {
        if (dataObj instanceof Map) {
            Map<String, Object> dataMap = (Map<String, Object>) dataObj;
            Object r = dataMap.get("records");
            if (r == null) r = dataMap.get("list");
            if (r instanceof List) return (List<Map<String, Object>>) r;
        }
        if (dataObj instanceof List) {
            List<?> list = (List<?>) dataObj;
            for (Object e : list) {
                if (!(e instanceof Map)) return null;
            }
            return (List<Map<String, Object>>) dataObj;
        }
        if (body != null) {
            Object r = body.get("records");
            if (r == null) r = body.get("list");
            if (r instanceof List) return (List<Map<String, Object>>) r;
        }
        return null;
    }

    private static int parseInt(Object v, int defaultValue) {
        if (v == null) return defaultValue;
        if (v instanceof Number) return ((Number) v).intValue();
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean validateUserId(String userId, Map<String, Object> config) throws BusinessException {
        return true;
    }

    /**
     * 返回 TimeStore API 的 origin（仅协议+主机，无路径），避免配置了带路径的 baseUrl（如 .../issuer/info）时拼出错误请求。
     * 内容拉取固定使用 /timeline/mymblog，资料拉取固定使用 /profile/detail。
     */
    private static String getBaseUrl(Map<String, Object> config) {
        String url = config != null ? (String) config.get("apiBaseUrl") : null;
        if (url == null || url.isEmpty()) {
            return "https://api.timestore.vip";
        }
        url = url.trim();
        try {
            java.net.URI uri = java.net.URI.create(url);
            String scheme = uri.getScheme() != null ? uri.getScheme() : "https";
            String host = uri.getHost();
            if (host == null || host.isEmpty()) {
                return stripPathToOrigin(url);
            }
            int port = uri.getPort();
            if (port <= 0 || ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80)) {
                return scheme + "://" + host;
            }
            return scheme + "://" + host + ":" + port;
        } catch (Exception e) {
            return stripPathToOrigin(url);
        }
    }

    /** 从任意 URL 中截取 origin（去掉路径），例如 https://api.timestore.vip/issuer/info -> https://api.timestore.vip */
    private static String stripPathToOrigin(String url) {
        if (url == null || url.isEmpty()) return "https://api.timestore.vip";
        url = url.trim();
        int afterScheme = url.indexOf("://");
        if (afterScheme <= 0) return "https://api.timestore.vip";
        int firstSlash = url.indexOf("/", afterScheme + 3);
        if (firstSlash < 0) {
            return url.replaceAll("/+$", "");
        }
        return url.substring(0, firstSlash);
    }

    /** 拼接 baseUrl + path + query，保证无多余斜杠（base 无尾斜杠，path 以 / 开头） */
    private static String buildUrl(String baseUrl, String path, String query) {
        String base = (baseUrl != null && !baseUrl.isEmpty()) ? baseUrl.replaceAll("/+$", "") : "https://api.timestore.vip";
        String p = (path != null && !path.isEmpty()) ? (path.startsWith("/") ? path : "/" + path) : "";
        String q = "";
        if (query != null && !query.isEmpty()) {
            q = query.startsWith("?") ? query : "?" + query;
        }
        return base + p + q;
    }

    /** 若未带 Bearer 前缀则自动加上（接口要求 mate-auth: Bearer <token>） */
    private static String normalizeMateAuth(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        String s = raw.trim();
        if (s.toLowerCase().startsWith("bearer ")) return s;
        return "Bearer " + s;
    }

    /**
     * 从 mate-auth JWT 中解析 userId（不校验签名，仅读取 payload）。
     * TimeStore mymblog 只返回 token 对应用户的博客，必须用此 uid 请求。
     */
    @SuppressWarnings("unchecked")
    private static String getUserIdFromMateAuthToken(String mateAuth) {
        if (mateAuth == null || mateAuth.isEmpty()) return null;
        String token = mateAuth.trim();
        if (token.toLowerCase().startsWith("bearer ")) token = token.substring(7).trim();
        int secondDot = token.indexOf('.', token.indexOf('.') + 1);
        if (secondDot <= 0) return null;
        String payloadB64 = token.substring(token.indexOf('.') + 1, secondDot);
        try {
            String payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> map = mapper.readValue(payload, Map.class);
            Object userId = map != null ? map.get("userId") : null;
            return userId != null ? userId.toString().trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String getMateAuth(Map<String, Object> config) {
        if (config == null) return null;
        String v = (String) config.get("mateAuth");
        if (v != null && !v.isEmpty()) return v;
        return (String) config.get("mate-auth");
    }

    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36";

    private static HttpHeaders createHeaders(String mateAuth) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("mate-auth", mateAuth);
        headers.set("Accept", "*/*");
        headers.set("Content-Type", "application/json");
        headers.set("User-Agent", USER_AGENT);
        headers.set("device", USER_AGENT);
        headers.set("language", "zh-CN");
        headers.set("platform", "h5");
        headers.set("origin", "https://web.timestore.vip");
        headers.set("referer", "https://web.timestore.vip/");
        return headers;
    }

    /** TimeStore 文章详情页（前端路由，timeid=记录 id） */
    private static final String TIMESTORE_DETAIL_URL = "https://web.timestore.vip/#/time/pages/timeDetail/index?timeid=%s";

    private static PlatformContent mapRecordToPlatformContent(Map<String, Object> record, String baseUrl) {
        PlatformContent content = new PlatformContent();
        // 默认发布时间，避免 DB 非空约束；解析到 ctime 后再覆盖
        content.setPublishedAt(LocalDateTime.now());
        Object idObj = record.get("id");
        String contentId = idObj != null ? idObj.toString() : null;
        content.setContentId(contentId);
        content.setTitle(truncateTitle((String) record.get("postContent"), 200));
        content.setBody((String) record.get("postContent"));
        // 使用官方详情页地址，便于在浏览器中打开
        content.setUrl(contentId != null ? String.format(TIMESTORE_DETAIL_URL, contentId) : baseUrl);
        content.setContentType(PlatformContent.ContentType.TEXT);
        content.setAuthorId(record.get("uid") != null ? record.get("uid").toString() : "me");
        content.setAuthorUsername(getString(record, "nickName", "TimeStore"));

        Object ctime = record.get("ctime");
        if (ctime == null) ctime = record.get("createTime");
        if (ctime == null) ctime = record.get("createdAt");
        if (ctime == null) ctime = record.get("time");
        if (ctime == null) ctime = record.get("ctimeStr");
        if (ctime instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) ctime;
            Object d = m.get("$date");
            if (d == null) d = m.get("date");
            if (d != null) ctime = d;
        }
        if (ctime instanceof Number) {
            long v = ((Number) ctime).longValue();
            long sec = v > 1_000_000_000_000L ? v / 1000 : v;
            content.setPublishedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(sec), ZoneId.systemDefault()));
        } else if (ctime != null) {
            String ctimeStr = ctime.toString().trim();
            try {
                if (ctimeStr.length() > 10 && (ctimeStr.contains("T") || ctimeStr.contains("+") || ctimeStr.contains("Z"))) {
                    // 转为服务器本地时间，与 endTime（服务器本地）比较一致
                    content.setPublishedAt(
                        ZonedDateTime.parse(ctimeStr).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime());
                } else if (!ctimeStr.isEmpty()) {
                    long v = Long.parseLong(ctimeStr.trim());
                    if (v > 0) {
                        long sec = v > 1_000_000_000_000L ? v / 1000 : v;
                        content.setPublishedAt(LocalDateTime.ofInstant(Instant.ofEpochSecond(sec), ZoneId.systemDefault()));
                    }
                }
            } catch (Exception ignored) {}
        }
        if (content.getPublishedAt() == null) {
            // 解析失败时用“过去时间”避免被 endTime 过滤掉（now 可能晚于请求时的 endTime）
            content.setPublishedAt(LocalDateTime.now().minusMinutes(10));
        }

        // img：支持逗号分隔多图，保存到 mediaUrls 供展示与持久化
        List<String> mediaUrls = new ArrayList<>();
        Object img = record.get("img");
        if (img instanceof String && !((String) img).isEmpty()) {
            for (String s : ((String) img).split(",")) {
                String u = s.trim();
                if (!u.isEmpty()) mediaUrls.add(u);
            }
        } else if (img instanceof List) {
            for (Object o : (List<?>) img) {
                if (o instanceof String && !((String) o).isEmpty()) mediaUrls.add((String) o);
            }
        }
        content.setMediaUrls(mediaUrls.isEmpty() ? null : mediaUrls);

        // 内容元数据：完整文章 JSON（含 nickName、userAvatar、img 等），便于展示与持久化
        content.setMetadata(new HashMap<>(record));
        return content;
    }

    private static String getString(Map<String, Object> map, String key, String defaultValue) {
        Object v = map == null ? null : map.get(key);
        if (v == null) return defaultValue;
        String s = v.toString().trim();
        return s.isEmpty() ? defaultValue : s;
    }

    private static String truncateTitle(String body, int maxLen) {
        if (body == null || body.isEmpty()) return "";
        String s = body.replaceAll("\\s+", " ").trim();
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
