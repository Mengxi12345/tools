package com.caat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 解析实际上传目录。从项目根目录启动时（如 IDE），uploads 解析为 project_root/uploads，
 * 但图片在 backend/uploads，需自动回退。
 */
@Slf4j
@Component
public class UploadDirResolver {

    private final Path resolvedPath;

    public UploadDirResolver(@Value("${app.upload-dir:uploads}") String uploadDir) {
        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        // 若配置路径下无 contents/images，尝试 backend/uploads（项目根启动场景）
        if (!Files.isDirectory(base) || !Files.isDirectory(base.resolve("contents/images"))) {
            Path alt = Paths.get("backend", "uploads").toAbsolutePath().normalize();
            if (Files.isDirectory(alt)) {
                base = alt;
                log.info("上传目录回退为 backend/uploads: {}", base);
            }
        }
        this.resolvedPath = base;
    }

    public Path getResolvedPath() {
        return resolvedPath;
    }

    public String getResolvedPathString() {
        return resolvedPath.toString();
    }
}
