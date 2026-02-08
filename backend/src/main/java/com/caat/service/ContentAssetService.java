package com.caat.service;

import com.caat.config.UploadDirResolver;
import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 内容附件：从 URL 下载图片或文件并保存到 uploads/contents/，用于正文展示。
 */
@Slf4j
@Service
public class ContentAssetService {

    private static final String IMAGES_SUBDIR = "contents/images";
    private static final String FILES_SUBDIR = "contents/files";
    private static final List<String> IMAGE_EXT = Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "svg");
    private static final int MAX_IMAGE_BYTES = 20 * 1024 * 1024; // 20MB，TimeStore 等平台图片可能较大
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024; // 50MB
    private static final Pattern FILENAME_DISPOSITION = Pattern.compile("filename[*]?=\\s*[\"']?([^\"';\\n]+)[\"']?", Pattern.CASE_INSENSITIVE);
    private static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final int DOWNLOAD_MAX_RETRIES = 3;
    private static final long DOWNLOAD_RETRY_DELAY_MS = 800;

    private final UploadDirResolver uploadDirResolver;
    private final RestTemplate restTemplate;
    private final RestTemplate timeStoreRestTemplate;

    /** TimeStore 图片域名，需使用放宽 SSL 的 RestTemplate */
    private static final String[] TIMESTORE_IMAGE_HOSTS = {"img.timestore.vip", "timestore.vip", "os-bucket-pm.oss-accelerate.aliyuncs.com"};

    /** img.timestore.vip 失败时尝试 OSS 直连（同一存储，路径可能兼容） */
    private static String toOssFallbackUrl(String url) {
        if (url == null || !url.startsWith("https://img.timestore.vip/")) return null;
        return "https://os-bucket-pm.oss-accelerate.aliyuncs.com/" + url.substring("https://img.timestore.vip/".length());
    }

    public ContentAssetService(UploadDirResolver uploadDirResolver,
                              RestTemplate restTemplate,
                              @Qualifier("timeStoreRestTemplate") RestTemplate timeStoreRestTemplate) {
        this.uploadDirResolver = uploadDirResolver;
        this.restTemplate = restTemplate;
        this.timeStoreRestTemplate = timeStoreRestTemplate;
    }

    private RestTemplate selectRestTemplate(String url) {
        if (url == null) return restTemplate;
        String lower = url.toLowerCase();
        for (String host : TIMESTORE_IMAGE_HOSTS) {
            if (lower.contains(host)) return timeStoreRestTemplate;
        }
        return restTemplate;
    }

    /**
     * 从 URL 下载图片并保存到 uploads/contents/images/，返回可访问路径。带重试。
     */
    public String downloadImageAndSave(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String trimmed = imageUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return trimmed;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG, MediaType.IMAGE_GIF,
                MediaType.parseMediaType("image/webp"), MediaType.parseMediaType("image/svg+xml")));
        headers.set("User-Agent", USER_AGENT);
        Exception lastEx = null;
        String[] urlsToTry = new String[]{trimmed};
        String ossFallback = toOssFallbackUrl(trimmed);
        if (ossFallback != null) urlsToTry = new String[]{trimmed, ossFallback};
        for (int attempt = 1; attempt <= DOWNLOAD_MAX_RETRIES; attempt++) {
            for (String urlToUse : urlsToTry) {
                RestTemplate client = selectRestTemplate(urlToUse);
                try {
                    ResponseEntity<byte[]> resp = client.exchange(
                        URI.create(urlToUse),
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        byte[].class);
                if (resp.getBody() == null || resp.getBody().length == 0) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "无法从该 URL 获取图片");
                }
                if (resp.getBody().length > MAX_IMAGE_BYTES) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 20MB");
                }
                String ext = extFromContentType(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
                if (ext == null) ext = extFromUrl(trimmed);
                if (ext == null || !IMAGE_EXT.contains(ext.toLowerCase(Locale.ROOT))) ext = "png";
                String filename = UUID.randomUUID() + "." + ext;
                Path dir = uploadDirResolver.getResolvedPath().resolve(IMAGES_SUBDIR).toAbsolutePath().normalize();
                Path target = dir.resolve(filename);
                Files.createDirectories(dir);
                Files.write(target, resp.getBody());
                if (!urlToUse.equals(trimmed)) {
                    log.info("下载图片成功（OSS 直连）: 原url={}", trimmed);
                } else if (attempt > 1) {
                    log.info("下载图片成功（第 {} 次重试）: url={}", attempt, trimmed);
                }
                return "/api/v1/uploads/" + IMAGES_SUBDIR + "/" + filename;
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                lastEx = e;
                if (!urlToUse.equals(trimmed)) {
                    log.warn("OSS 直连也失败: url={}, error={}", urlToUse, e.getMessage());
                }
            }
            }
            if (attempt < DOWNLOAD_MAX_RETRIES) {
                log.warn("下载内容图片失败，第 {}/{} 次，将重试: url={}, error={}", attempt, DOWNLOAD_MAX_RETRIES, trimmed, lastEx != null ? lastEx.getMessage() : "");
                try {
                    Thread.sleep(DOWNLOAD_RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "下载被中断");
                }
            } else {
                log.warn("下载内容图片失败（已重试 {} 次）: url={}, error={}", DOWNLOAD_MAX_RETRIES, trimmed, lastEx != null ? lastEx.getMessage() : "");
            }
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "下载图片失败：" + (lastEx != null ? lastEx.getMessage() : "未知错误"));
    }

    /**
     * 从 URL 下载文件并保存到 uploads/contents/files/，返回可访问路径。
     * suggestedExt 可为 null，则从 Content-Type 或 URL 推断，否则用 .bin。
     */
    public String downloadFileAndSave(String fileUrl, String suggestedExt) {
        if (fileUrl == null || fileUrl.isBlank()) return null;
        String trimmed = fileUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return trimmed;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
            headers.set("User-Agent", USER_AGENT);
            RestTemplate client = selectRestTemplate(trimmed);
            ResponseEntity<byte[]> resp = client.exchange(
                    URI.create(trimmed),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class);
            if (resp.getBody() == null || resp.getBody().length == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无法从该 URL 获取文件");
            }
            if (resp.getBody().length > MAX_FILE_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文件大小不能超过 50MB");
            }
            String ext = suggestedExt;
            if (ext == null || ext.isBlank()) {
                String disposition = resp.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
                if (disposition != null) {
                    java.util.regex.Matcher m = FILENAME_DISPOSITION.matcher(disposition);
                    if (m.find()) {
                        String name = m.group(1).trim();
                        int i = name.lastIndexOf('.');
                        if (i > 0) ext = name.substring(i + 1).toLowerCase(Locale.ROOT);
                    }
                }
            }
            if (ext == null || ext.isBlank()) {
                ext = extFromContentType(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            }
            if (ext == null || ext.isBlank()) ext = extFromUrl(trimmed);
            if (ext == null || ext.isBlank()) ext = "bin";
            String filename = UUID.randomUUID() + "." + ext.replaceAll("[^a-zA-Z0-9]", "");
            Path dir = uploadDirResolver.getResolvedPath().resolve(FILES_SUBDIR).toAbsolutePath().normalize();
            Path target = dir.resolve(filename);
            Files.createDirectories(dir);
            Files.write(target, resp.getBody());
            return "/api/v1/uploads/" + FILES_SUBDIR + "/" + filename;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("下载内容文件失败: url={}, error={}", trimmed, e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下载文件失败：" + e.getMessage());
        }
    }

    private static String extFromContentType(String ct) {
        if (ct == null) return null;
        if (ct.contains("png")) return "png";
        if (ct.contains("jpeg") || ct.contains("jpg")) return "jpg";
        if (ct.contains("gif")) return "gif";
        if (ct.contains("webp")) return "webp";
        if (ct.contains("svg")) return "svg";
        if (ct.contains("pdf")) return "pdf";
        if (ct.contains("zip")) return "zip";
        return null;
    }

    private static String extFromUrl(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        String path = q > 0 ? url.substring(0, q) : url;
        int i = path.lastIndexOf('.');
        return i > 0 ? path.substring(i + 1).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") : null;
    }

    /**
     * 根据 URL 删除本地文件。仅处理 /api/v1/uploads/ 开头的本地上传路径。
     * @param url 如 /api/v1/uploads/contents/images/xxx.png 或带 origin 的完整 URL
     * @return true 表示已删除，false 表示未删除（非本地路径或文件不存在）
     */
    public boolean deleteLocalFileByUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String u = url.trim();
        // 提取 /api/v1/uploads/ 之后的相对路径
        String prefix = "/api/v1/uploads/";
        int idx = u.indexOf(prefix);
        if (idx < 0) {
            // 可能是完整 URL，如 https://xxx/api/v1/uploads/contents/images/xxx.png
            idx = u.lastIndexOf(prefix);
            if (idx < 0) return false;
        }
        String relPath = u.substring(idx + prefix.length());
        if (relPath.isEmpty()) return false;
        try {
            Path base = uploadDirResolver.getResolvedPath().toAbsolutePath().normalize();
            Path target = base.resolve(relPath).toAbsolutePath().normalize();
            if (!target.startsWith(base)) {
                log.warn("拒绝删除非 uploads 目录下的文件: url={}", url);
                return false;
            }
            if (Files.exists(target) && Files.isRegularFile(target)) {
                Files.delete(target);
                log.info("已删除内容关联文件: {}", relPath);
                return true;
            }
        } catch (Exception e) {
            log.warn("删除本地上传文件失败: url={}, error={}", url, e.getMessage());
        }
        return false;
    }
}
