package com.caat.service;

import com.caat.exception.BusinessException;
import com.caat.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final int MAX_FILE_BYTES = 50 * 1024 * 1024; // 50MB
    private static final Pattern FILENAME_DISPOSITION = Pattern.compile("filename[*]?=\\s*[\"']?([^\"';\\n]+)[\"']?", Pattern.CASE_INSENSITIVE);

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private final RestTemplate restTemplate;

    public ContentAssetService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 从 URL 下载图片并保存到 uploads/contents/images/，返回可访问路径。
     */
    public String downloadImageAndSave(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        String trimmed = imageUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return trimmed;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(Arrays.asList(MediaType.IMAGE_PNG, MediaType.IMAGE_JPEG, MediaType.IMAGE_GIF,
                    MediaType.parseMediaType("image/webp"), MediaType.parseMediaType("image/svg+xml")));
            ResponseEntity<byte[]> resp = restTemplate.exchange(
                    URI.create(trimmed),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    byte[].class);
            if (resp.getBody() == null || resp.getBody().length == 0) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无法从该 URL 获取图片");
            }
            if (resp.getBody().length > MAX_IMAGE_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 5MB");
            }
            String ext = extFromContentType(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            if (ext == null) ext = extFromUrl(trimmed);
            if (ext == null || !IMAGE_EXT.contains(ext.toLowerCase(Locale.ROOT))) ext = "png";
            String filename = UUID.randomUUID() + "." + ext;
            Path dir = Paths.get(uploadDir, IMAGES_SUBDIR).toAbsolutePath().normalize();
            Path target = dir.resolve(filename);
            Files.createDirectories(dir);
            Files.write(target, resp.getBody());
            return "/api/v1/uploads/" + IMAGES_SUBDIR + "/" + filename;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("下载内容图片失败: url={}, error={}", trimmed, e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下载图片失败：" + e.getMessage());
        }
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
            ResponseEntity<byte[]> resp = restTemplate.exchange(
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
            Path dir = Paths.get(uploadDir, FILES_SUBDIR).toAbsolutePath().normalize();
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
}
