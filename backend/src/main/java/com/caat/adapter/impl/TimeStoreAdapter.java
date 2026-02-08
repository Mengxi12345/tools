package com.caat.adapter.impl;

import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.FetchResult;
import com.caat.adapter.model.PlatformContent;
import com.caat.adapter.model.PlatformUser;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
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
    /** 文章请求失败时最大重试次数 */
    private static final int MAX_RETRIES = 10;
    /** 重试间隔随机范围：最小 2 秒，最大 8 秒 */
    private static final int RETRY_DELAY_MIN_MS = 2000;
    private static final int RETRY_DELAY_MAX_MS = 8000;
    /** 真实 API：我的博客列表（api.timestore.vip） */
    private static final String DEFAULT_MYBLOG_PATH = "/timeline/mymblog";
    /** 单篇文章详情（api.timestore.vip，根据 postId 直接拉取） */
    private static final String TIMELINE_SHOW_PATH = "/timeline/show";
    /** 用户资料：头像、简介（api.timestore.vip） */
    private static final String PROFILE_DETAIL_PATH = "/profile/detail";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public TimeStoreAdapter(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
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
     * 执行 GET 请求，失败时重试最多 MAX_RETRIES 次，每次间隔随机时间。
     * 用于文章列表等请求，统一在底层处理网络抖动、限流等情况。
     */
    private ResponseEntity<Map> executeGetWithRetry(String url, HttpEntity<String> entity, String caller) throws BusinessException {
        Exception lastEx = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
                if (attempt > 1) {
                    log.info("TimeStore {} 第 {} 次重试成功: url={}", caller, attempt, url);
                }
                return response;
            } catch (Exception e) {
                lastEx = e;
                if (attempt < MAX_RETRIES) {
                    int delayMs = RETRY_DELAY_MIN_MS + ThreadLocalRandom.current().nextInt(RETRY_DELAY_MAX_MS - RETRY_DELAY_MIN_MS + 1);
                    log.warn("TimeStore {} 请求失败，第 {}/{} 次，{}ms 后重试: url={}, error={}",
                        caller, attempt, MAX_RETRIES, delayMs, url, e.getMessage());
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "请求被中断: " + e.getMessage());
                    }
                } else {
                    log.error("TimeStore {} 请求失败，已重试 {} 次: url={}", caller, MAX_RETRIES, url, e);
                }
            }
        }
        throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED,
            "获取内容失败（已重试 " + MAX_RETRIES + " 次）: " + (lastEx != null ? lastEx.getMessage() : "未知错误"));
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
            ResponseEntity<Map> response = executeGetWithRetry(url, entity, "getUserContents");
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

    /**
     * 根据文章 URL 直接拉取单篇文章内容
     * URL 格式：https://web.timestore.vip/#/time/pages/timeDetail/index?timeid={contentId}
     * 使用 /timeline/show?postId={contentId} API 直接拉取，无需 uid
     */
    @Override
    public Optional<PlatformContent> fetchContentByUrl(String articleUrl, Map<String, Object> config) throws BusinessException {
        return fetchContentByUrl(articleUrl, null, null, config);
    }

    /**
     * 根据文章 URL 拉取单篇文章（重载方法）
     * 优先使用 /timeline/show?postId=xxx 接口；
     */
    public Optional<PlatformContent> fetchContentByUrl(String articleUrl, String authorUid, LocalDateTime publishedAt, Map<String, Object> config) throws BusinessException {
        String contentId = extractTimeidFromUrl(articleUrl);
        if (contentId == null || contentId.isEmpty()) {
            log.warn("TimeStore fetchContentByUrl: 无法从 URL 提取 timeid, url={}", articleUrl);
            return Optional.empty();
        }
        String baseUrl = getBaseUrl(config);
        String token = getMateAuth(config);
        if (token == null || token.isEmpty()) {
            throw new BusinessException(ErrorCode.PLATFORM_CONNECTION_FAILED, "TimeStore mate-auth 未配置");
        }
        try {
            // 使用 /timeline/show?postId=xxx 接口（正确 API，直接按 id 拉取）
            Optional<PlatformContent> fromShow = fetchByShowApi(baseUrl, contentId, token);
            if (fromShow.isPresent()) {
                return fromShow;
            }
            return Optional.empty();
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("TimeStore fetchContentByUrl 异常: timeid={}, error={}", contentId, e.getMessage(), e);
            return Optional.empty();
        }
    }

    /** 使用 /timeline/show?postId=xxx 接口拉取单篇文章 */
    private Optional<PlatformContent> fetchByShowApi(String baseUrl, String postId, String token) {
        String query = "postId=" + postId;
        String url = buildUrl(baseUrl, TIMELINE_SHOW_PATH, query);
        log.info("TimeStore fetchContentByUrl: 使用 show 接口 postId={}, url={}", postId, url);
        try {
            HttpHeaders headers = createHeaders(normalizeMateAuth(token));
            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            String bodyStr = response.getBody();
            if (!response.getStatusCode().is2xxSuccessful() || bodyStr == null || bodyStr.isBlank()) {
                log.warn("TimeStore fetchByShowApi: 请求失败 postId={}, status={}", postId, response.getStatusCode());
                return Optional.empty();
            }
            String contentType = response.getHeaders().getFirst("Content-Type");
            if (isHtmlResponse(contentType, bodyStr)) {
                log.warn("TimeStore fetchByShowApi: 返回 HTML 而非 JSON postId={}", postId);
                return Optional.empty();
            }
            Map<String, Object> body = parseJsonToMap(bodyStr, "show:postId=" + postId);
            if (body == null) {
                return Optional.empty();
            }
            Object code = body.get("code");
            Object msg = body.get("msg");
            int codeVal = (code instanceof Number) ? ((Number) code).intValue() : 0;
            if (codeVal != 200) {
                log.warn("TimeStore fetchByShowApi: API 返回非成功 postId={}, code={}, msg={}", postId, code, msg);
                return Optional.empty();
            }
            Object dataObj = body.get("data");
            if (dataObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> record = (Map<String, Object>) dataObj;
                PlatformContent content = mapRecordToPlatformContent(record, "https://web.timestore.vip");
                log.info("TimeStore fetchByShowApi: 成功拉取文章 postId={}", postId);
                return Optional.of(content);
            }
            log.warn("TimeStore fetchByShowApi: data 非对象 postId={}", postId);
            return Optional.empty();
        } catch (Exception e) {
            log.warn("TimeStore fetchByShowApi 异常: postId={}, error={}", postId, e.getMessage());
            return Optional.empty();
        }
    }

    private static String extractTimeidFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        int idx = url.indexOf("timeid=");
        if (idx < 0) return null;
        int start = idx + 7;
        int end = url.indexOf("&", start);
        if (end < 0) end = url.length();
        String s = url.substring(start, end).trim();
        return s.isEmpty() ? null : s;
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
    private String getUserIdFromMateAuthToken(String mateAuth) {
        if (mateAuth == null || mateAuth.isEmpty()) return null;
        String token = mateAuth.trim();
        if (token.toLowerCase().startsWith("bearer ")) token = token.substring(7).trim();
        int secondDot = token.indexOf('.', token.indexOf('.') + 1);
        if (secondDot <= 0) return null;
        String payloadB64 = token.substring(token.indexOf('.') + 1, secondDot);
        try {
            String payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(payload, Map.class);
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

    /**
     * 判断响应是否为 HTML（根据 Content-Type 与内容首字符）
     */
    private static boolean isHtmlResponse(String contentType, String bodyStr) {
        if (contentType != null && contentType.toLowerCase().contains("text/html")) {
            return true;
        }
        if (bodyStr == null) return false;
        String trimmed = bodyStr.trim();
        return !trimmed.isEmpty() && trimmed.charAt(0) == '<';
    }

    /**
     * 使用共享 ObjectMapper 将 JSON 字符串解析为 Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonToMap(String bodyStr, String context) {
        try {
            return objectMapper.readValue(bodyStr, Map.class);
        } catch (Exception e) {
            log.warn("TimeStore JSON 解析失败: context={}, error={}", context, e.getMessage());
            return null;
        }
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

        // 图片：如果 extVO 为空，使用顶层 img；否则从 extVO.extLiveVOS[].img 提取，如果没有则回退到 img
        List<String> mediaUrls = new ArrayList<>();
        Object extVO = record.get("extVO");
        // 如果 extVO 为空（null 或不存在），直接使用顶层 img
        if (extVO == null) {
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
        } else if (extVO instanceof Map) {
            // extVO 存在，优先从 extLiveVOS 提取图片
            @SuppressWarnings("unchecked")
            Map<String, Object> extVOMap = (Map<String, Object>) extVO;
            Object extLiveVOS = extVOMap.get("extLiveVOS");
            if (extLiveVOS instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> liveList = (List<Map<String, Object>>) extLiveVOS;
                for (Map<String, Object> live : liveList) {
                    Object imgObj = live.get("img");
                    if (imgObj instanceof String && !((String) imgObj).isEmpty()) {
                        mediaUrls.add(((String) imgObj).trim());
                    }
                }
            }
            // 如果 extLiveVOS 中没有图片，回退到顶层 img 字段
            if (mediaUrls.isEmpty()) {
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
            }
        } else {
            // extVO 存在但不是 Map，回退到顶层 img
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
