package com.caat.service;

import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 知识星球文件下载：通过 GET /v2/files/{file_id}/download_url 获取下载地址。
 */
@Slf4j
@Service
public class ZsxqFileService {

    private static final String API_BASE = "https://api.zsxq.com/v2";

    private final RestTemplate restTemplate;

    public ZsxqFileService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 获取文件下载地址。GET https://api.zsxq.com/v2/files/{fileId}/download_url
     * @param fileId 文件 ID（来自 talk.files[].file_id）
     * @param config 平台配置（含 accessToken）
     * @return 可下载的 URL，失败返回 null
     */
    @SuppressWarnings("unchecked")
    public String getFileDownloadUrl(String fileId, Map<String, Object> config) {
        if (fileId == null || fileId.isEmpty()) return null;
        String url = API_BASE + "/files/" + fileId + "/download_url";
        try {
            HttpEntity<String> entity = new HttpEntity<>(createHeaders(config));
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            Map<String, Object> body = response.getBody();
            if (!Boolean.TRUE.equals(body.get("succeeded"))) {
                return null;
            }
            Map<String, Object> respData = (Map<String, Object>) body.get("resp_data");
            if (respData == null) return null;
            Object downloadUrl = respData.get("download_url");
            return downloadUrl != null ? downloadUrl.toString() : null;
        } catch (Exception e) {
            log.warn("获取知识星球文件下载地址失败: fileId={}, error={}", fileId, e.getMessage());
            return null;
        }
    }

    private static HttpHeaders createHeaders(Map<String, Object> config) {
        String token = getAccessToken(config);
        HttpHeaders headers = new HttpHeaders();
        headers.set("accept", "application/json, text/plain, */*");
        headers.set("origin", "https://wx.zsxq.com");
        headers.set("referer", "https://wx.zsxq.com/");
        headers.set("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36");
        headers.set("x-version", "2.88.0");
        headers.set("x-timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        headers.set("Cookie", "zsxq_access_token=" + token);
        return headers;
    }

    private static String getAccessToken(Map<String, Object> config) {
        if (config == null) return "";
        Object t = config.get("accessToken");
        if (t == null) t = config.get("zsxq_access_token");
        return t != null ? t.toString() : "";
    }
}
