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
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 用户（追踪用户）头像：上传文件或从 URL 下载并保存到本地，返回可访问路径。
 */
@Slf4j
@Service
public class UserAvatarService {

    private static final String SUBDIR = "users";
    private static final List<String> ALLOWED_EXT = Arrays.asList("png", "jpg", "jpeg", "gif", "webp", "svg");
    private static final int MAX_SIZE_BYTES = 2 * 1024 * 1024; // 2MB

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private final RestTemplate restTemplate;

    public UserAvatarService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * 保存上传的文件到 uploads/users/，返回访问路径（如 /api/v1/uploads/users/xxx.png）
     */
    public String saveUploadedFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择图片文件");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 2MB");
        }
        String ext = getExtensionFromFilename(file.getOriginalFilename());
        if (ext == null || !ALLOWED_EXT.contains(ext.toLowerCase(Locale.ROOT))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅支持图片格式：png、jpg、jpeg、gif、webp、svg");
        }
        String filename = UUID.randomUUID() + "." + ext;
        Path dir = Paths.get(uploadDir, SUBDIR).toAbsolutePath().normalize();
        Path target = dir.resolve(filename);
        try {
            Files.createDirectories(dir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.warn("保存用户头像上传文件失败: {}", e.getMessage());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存图片失败");
        }
        return "/api/v1/uploads/" + SUBDIR + "/" + filename;
    }

    /**
     * 从 URL 下载图片并保存到本地，返回访问路径。若下载失败则抛出业务异常。
     */
    public String downloadAndSave(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String trimmed = imageUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return trimmed; // 已是本地路径等，不处理
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
            if (resp.getBody().length > MAX_SIZE_BYTES) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "图片大小不能超过 2MB");
            }
            String ext = getExtensionFromContentType(resp.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE));
            if (ext == null) {
                ext = getExtensionFromUrl(trimmed);
            }
            if (ext == null || !ALLOWED_EXT.contains(ext.toLowerCase(Locale.ROOT))) {
                ext = "png";
            }
            String filename = UUID.randomUUID() + "." + ext;
            Path dir = Paths.get(uploadDir, SUBDIR).toAbsolutePath().normalize();
            Path target = dir.resolve(filename);
            Files.createDirectories(dir);
            Files.write(target, resp.getBody());
            return "/api/v1/uploads/" + SUBDIR + "/" + filename;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("从 URL 下载用户头像失败: url={}, error={}", trimmed, e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "下载图片失败：" + e.getMessage());
        }
    }

    private static String getExtensionFromFilename(String filename) {
        if (filename == null || filename.isEmpty()) return null;
        int i = filename.lastIndexOf('.');
        return i > 0 ? filename.substring(i + 1).toLowerCase(Locale.ROOT) : null;
    }

    private static String getExtensionFromContentType(String contentType) {
        if (contentType == null) return null;
        if (contentType.contains("png")) return "png";
        if (contentType.contains("jpeg") || contentType.contains("jpg")) return "jpg";
        if (contentType.contains("gif")) return "gif";
        if (contentType.contains("webp")) return "webp";
        if (contentType.contains("svg")) return "svg";
        return null;
    }

    private static String getExtensionFromUrl(String url) {
        if (url == null) return null;
        int q = url.indexOf('?');
        String path = q > 0 ? url.substring(0, q) : url;
        int i = path.lastIndexOf('.');
        return i > 0 ? path.substring(i + 1).toLowerCase(Locale.ROOT) : null;
    }
}
