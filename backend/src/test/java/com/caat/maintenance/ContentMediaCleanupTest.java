package com.caat.maintenance;

import com.caat.adapter.PlatformAdapter;
import com.caat.adapter.model.PlatformContent;
import com.caat.entity.Content;
import com.caat.entity.Platform;
import com.caat.repository.ContentRepository;
import com.caat.repository.PlatformRepository;
import com.caat.service.ContentAssetService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 杂项维护工具（测试模块使用），用于清理 uploads/contents/images 下的孤儿图片。
 *
 * 注意：
 * - 该类放在 test 模块中，仅在手动执行对应测试方法时才会生效；
 * - 请务必确认数据库连接和 uploads 目录指向的是「期望环境」再运行。
 */
@Slf4j
@SpringBootTest
@ActiveProfiles("dev")
public class ContentMediaCleanupTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private ContentRepository contentRepository;
    
    @Autowired
    private PlatformRepository platformRepository;
    
    @Autowired
    @Qualifier("timeStoreAdapter")
    private PlatformAdapter timeStoreAdapter;
    
    @Autowired
    private ContentAssetService contentAssetService;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 测试模式：仅检查第一张图片，不实际删除，用于验证逻辑是否正确。
     */
    @Test
    void testCheckFirstImageOnly() throws IOException {
        Path imagesDir = Paths.get("..", "uploads", "contents", "images").normalize();
        if (Files.notExists(imagesDir) || !Files.isDirectory(imagesDir)) {
            log.warn("图片目录不存在或不是目录，跳过处理: {}", imagesDir.toAbsolutePath());
            return;
        }

        log.info("【测试模式】开始检查第一张图片，目录: {}", imagesDir.toAbsolutePath());

        try (Stream<Path> stream = Files.list(imagesDir)) {
            Path firstImage = stream
                .filter(Files::isRegularFile)
                .filter(path -> isImageFile(path.getFileName().toString()))
                .findFirst()
                .orElse(null);

            if (firstImage == null) {
                log.info("未找到任何图片文件");
                return;
            }

            String fileName = firstImage.getFileName().toString();
            String mediaUrl = "/api/v1/uploads/contents/images/" + fileName;

            log.info("检查图片: fileName={}, mediaUrl={}", fileName, mediaUrl);

            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM content_media_urls WHERE media_url = ?",
                Integer.class,
                mediaUrl
            );

            if (count == null || count == 0) {
                log.warn("【测试结果】该图片未被引用，在实际执行删除时会删除此文件: fileName={}, mediaUrl={}", fileName, mediaUrl);
            } else {
                log.info("【测试结果】该图片已被引用（引用次数={}），不会删除: fileName={}, mediaUrl={}", count, fileName, mediaUrl);
            }
        }
    }

    /**
     * 检查接口：统计有多少张图片不存在引用（不执行任何删除或移动操作）。
     */
    @Test
    void checkOrphanImagesCount() throws IOException {
        Path imagesDir = Paths.get("..", "uploads", "contents", "images").normalize();
        if (Files.notExists(imagesDir) || !Files.isDirectory(imagesDir)) {
            log.warn("图片目录不存在或不是目录，跳过处理: {}", imagesDir.toAbsolutePath());
            return;
        }

        log.info("开始检查未被引用的图片数量，目录: {}", imagesDir.toAbsolutePath());

        AtomicInteger totalCount = new AtomicInteger();
        AtomicInteger orphanCount = new AtomicInteger();
        AtomicInteger referencedCount = new AtomicInteger();

        try (Stream<Path> stream = Files.list(imagesDir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> isImageFile(path.getFileName().toString()))
                .forEach(path -> {
                    totalCount.incrementAndGet();
                    String fileName = path.getFileName().toString();
                    String mediaUrl = "/api/v1/uploads/contents/images/" + fileName;

                    Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM content_media_urls WHERE media_url = ?",
                        Integer.class,
                        mediaUrl
                    );

                    if (count == null || count == 0) {
                        orphanCount.incrementAndGet();
                        log.debug("未被引用的图片: fileName={}, mediaUrl={}", fileName, mediaUrl);
                    } else {
                        referencedCount.incrementAndGet();
                    }
                });
        }

        log.info("检查完成，总图片数={}，未被引用图片数={}，已被引用图片数={}",
                totalCount.get(), orphanCount.get(), referencedCount.get());
    }

    /**
     * 将未被引用的图片移动到 remove 目录下（而不是直接删除）。
     * 执行前建议先运行 checkOrphanImagesCount() 查看统计信息。
     */
    @Test
    void moveOrphanImagesToRemoveDirectory() throws IOException {
        // 路径指向后端目录上一级的 uploads 目录
        Path imagesDir = Paths.get("..", "uploads", "contents", "images").normalize();
        if (Files.notExists(imagesDir) || !Files.isDirectory(imagesDir)) {
            log.warn("图片目录不存在或不是目录，跳过处理: {}", imagesDir.toAbsolutePath());
            return;
        }

        // remove 目录：与 images 目录同级
        Path removeDir = imagesDir.getParent().resolve("remove");
        if (!Files.exists(removeDir)) {
            Files.createDirectories(removeDir);
            log.info("已创建 remove 目录: {}", removeDir.toAbsolutePath());
        }

        log.info("开始移动未被引用的图片到 remove 目录，源目录: {}, 目标目录: {}", 
                imagesDir.toAbsolutePath(), removeDir.toAbsolutePath());

        AtomicInteger totalCount = new AtomicInteger();
        AtomicInteger movedCount = new AtomicInteger();
        AtomicInteger keptCount = new AtomicInteger();

        try (Stream<Path> stream = Files.list(imagesDir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> isImageFile(path.getFileName().toString()))
                .forEach(path -> {
                    totalCount.incrementAndGet();
                    String fileName = path.getFileName().toString();
                    // 与后端上传接口返回的路径保持一致
                    String mediaUrl = "/api/v1/uploads/contents/images/" + fileName;

                    Integer count = jdbcTemplate.queryForObject(
                        "SELECT COUNT(1) FROM content_media_urls WHERE media_url = ?",
                        Integer.class,
                        mediaUrl
                    );

                    if (count == null || count == 0) {
                        try {
                            Path targetPath = removeDir.resolve(fileName);
                            // 如果目标文件已存在，添加时间戳后缀避免冲突
                            if (Files.exists(targetPath)) {
                                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                                String extension = fileName.substring(fileName.lastIndexOf('.'));
                                String timestamp = String.valueOf(System.currentTimeMillis());
                                targetPath = removeDir.resolve(baseName + "_" + timestamp + extension);
                            }
                            Files.move(path, targetPath);
                            movedCount.incrementAndGet();
                            log.info("已移动未被引用的图片文件: fileName={}, mediaUrl={}, target={}", 
                                    fileName, mediaUrl, targetPath.getFileName());
                        } catch (IOException e) {
                            log.warn("移动图片文件失败: fileName={}, mediaUrl={}, error={}", fileName, mediaUrl, e.getMessage());
                        }
                    } else {
                        keptCount.incrementAndGet();
                    }
                });
        }

        log.info("图片移动完成，总文件数={}，已移动到 remove 目录={}，保留文件={}",
                totalCount.get(), movedCount.get(), keptCount.get());
    }

    /**
     * 检查有多少张图片是空白或模糊的。
     * 如果发现模糊图片，则复制到 blank 目录下。
     */
    @Test
    void checkBlankOrBlurredImages() throws IOException {
        Path imagesDir = Paths.get("..", "uploads", "contents", "images").normalize();
        if (Files.notExists(imagesDir) || !Files.isDirectory(imagesDir)) {
            log.warn("图片目录不存在或不是目录，跳过处理: {}", imagesDir.toAbsolutePath());
            return;
        }

        // blank 目录：与 images 目录同级
        Path blankDir = imagesDir.getParent().resolve("blank");
        if (!Files.exists(blankDir)) {
            Files.createDirectories(blankDir);
            log.info("已创建 blank 目录: {}", blankDir.toAbsolutePath());
        }

        log.info("开始检查空白或模糊图片，目录: {}", imagesDir.toAbsolutePath());
        log.info("模糊图片将复制到: {}", blankDir.toAbsolutePath());

        AtomicInteger totalCount = new AtomicInteger();
        AtomicInteger blankCount = new AtomicInteger();
        AtomicInteger blurredCount = new AtomicInteger();
        AtomicInteger copiedCount = new AtomicInteger();
        AtomicInteger normalCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        try (Stream<Path> stream = Files.list(imagesDir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> isImageFile(path.getFileName().toString()))
                .forEach(path -> {
                    totalCount.incrementAndGet();
                    String fileName = path.getFileName().toString();
                    
                    try {
                        BufferedImage image = ImageIO.read(path.toFile());
                        if (image == null) {
                            errorCount.incrementAndGet();
                            log.warn("无法读取图片文件: fileName={}", fileName);
                            return;
                        }

                        boolean isBlank = isBlankImage(image);
//                        boolean isBlurred = isBlurredImage(image);
                        boolean isBlurred = false;

                        if (isBlank) {
                            blankCount.incrementAndGet();
                            log.warn("检测到空白图片: fileName={}", fileName);
                        } else if (isBlurred) {
                            blurredCount.incrementAndGet();
                            log.warn("检测到模糊图片: fileName={}", fileName);
                            
                            // 复制模糊图片到 blank 目录
                            try {
                                Path targetPath = blankDir.resolve(fileName);
                                // 如果目标文件已存在，添加时间戳后缀避免冲突
                                if (Files.exists(targetPath)) {
                                    String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                                    String extension = fileName.substring(fileName.lastIndexOf('.'));
                                    String timestamp = String.valueOf(System.currentTimeMillis());
                                    targetPath = blankDir.resolve(baseName + "_" + timestamp + extension);
                                }
                                Files.copy(path, targetPath);
                                copiedCount.incrementAndGet();
                                log.info("已复制模糊图片到 blank 目录: fileName={}, target={}", 
                                        fileName, targetPath.getFileName());
                            } catch (IOException e) {
                                log.warn("复制模糊图片失败: fileName={}, error={}", fileName, e.getMessage());
                            }
                        } else {
                            normalCount.incrementAndGet();
                        }
                    } catch (IOException e) {
                        errorCount.incrementAndGet();
                        log.warn("处理图片文件失败: fileName={}, error={}", fileName, e.getMessage());
                    }
                });
        }

        log.info("检查完成，总图片数={}，空白图片={}，模糊图片={}（已复制到 blank 目录={}），正常图片={}，处理失败={}",
                totalCount.get(), blankCount.get(), blurredCount.get(), copiedCount.get(), normalCount.get(), errorCount.get());
    }

    /**
     * 判断图片是否为空白（全白、全黑或单色）。
     * 通过检查像素值的方差来判断：如果方差很小，说明图片接近单色。
     */
    private boolean isBlankImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        if (width == 0 || height == 0) {
            return true;
        }

        // 采样检查（每10个像素采样一次，提高性能）
        int sampleStep = Math.max(1, Math.min(width, height) / 50);
        long sumR = 0, sumG = 0, sumB = 0;
        long sumSqR = 0, sumSqG = 0, sumSqB = 0;
        int sampleCount = 0;

        for (int y = 0; y < height; y += sampleStep) {
            for (int x = 0; x < width; x += sampleStep) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                
                sumR += r;
                sumG += g;
                sumB += b;
                sumSqR += (long) r * r;
                sumSqG += (long) g * g;
                sumSqB += (long) b * b;
                sampleCount++;
            }
        }

        if (sampleCount == 0) {
            return true;
        }

        // 计算方差
        double meanR = (double) sumR / sampleCount;
        double meanG = (double) sumG / sampleCount;
        double meanB = (double) sumB / sampleCount;
        
        double varianceR = ((double) sumSqR / sampleCount) - (meanR * meanR);
        double varianceG = ((double) sumSqG / sampleCount) - (meanG * meanG);
        double varianceB = ((double) sumSqB / sampleCount) - (meanB * meanB);
        
        double avgVariance = (varianceR + varianceG + varianceB) / 3.0;
        
        // 如果方差小于阈值（例如 100），认为是空白图片
        // 这个阈值可以根据实际情况调整
        return avgVariance < 100.0;
    }

    /**
     * 判断图片是否模糊。
     * 使用拉普拉斯算子检测边缘清晰度：模糊图片的边缘检测结果方差较小。
     */
    private boolean isBlurredImage(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        if (width < 3 || height < 3) {
            return false; // 太小无法判断
        }

        // 转换为灰度图并应用拉普拉斯算子
        // 拉普拉斯算子：[[0, -1, 0], [-1, 4, -1], [0, -1, 0]]
        long sumSquared = 0;
        int edgePixelCount = 0;
        
        // 采样处理（每5个像素采样一次，提高性能）
        int sampleStep = 5;
        
        for (int y = 1; y < height - 1; y += sampleStep) {
            for (int x = 1; x < width - 1; x += sampleStep) {
                // 获取周围像素的灰度值
                int grayCenter = getGrayValue(image.getRGB(x, y));
                int grayTop = getGrayValue(image.getRGB(x, y - 1));
                int grayBottom = getGrayValue(image.getRGB(x, y + 1));
                int grayLeft = getGrayValue(image.getRGB(x - 1, y));
                int grayRight = getGrayValue(image.getRGB(x + 1, y));
                
                // 拉普拉斯算子计算
                int laplacian = Math.abs(4 * grayCenter - grayTop - grayBottom - grayLeft - grayRight);
                sumSquared += (long) laplacian * laplacian;
                edgePixelCount++;
            }
        }

        if (edgePixelCount == 0) {
            return false;
        }

        // 计算拉普拉斯响应的方差
        double variance = (double) sumSquared / edgePixelCount;
        
        // 如果方差小于阈值（例如 100），认为是模糊图片
        // 这个阈值可以根据实际情况调整
        return variance < 100.0;
    }

    /**
     * 获取RGB值的灰度值（使用标准公式）。
     */
    private int getGrayValue(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        // 标准灰度转换公式
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    /**
     * 遍历 blank 目录下的图片，根据图片地址获取对应的文章 ID。
     */
    @Test
    void getContentIdsForBlankImages() throws IOException {
        Path blankDir = Paths.get("..", "uploads", "contents", "blank").normalize();
        if (Files.notExists(blankDir) || !Files.isDirectory(blankDir)) {
            log.warn("blank 目录不存在或不是目录，跳过处理: {}", blankDir.toAbsolutePath());
            return;
        }

        log.info("开始遍历 blank 目录下的图片，获取对应的文章 ID，目录: {}", blankDir.toAbsolutePath());

        AtomicInteger totalCount = new AtomicInteger();
        AtomicInteger foundCount = new AtomicInteger();
        AtomicInteger notFoundCount = new AtomicInteger();

        try (Stream<Path> stream = Files.list(blankDir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> isImageFile(path.getFileName().toString()))
                .forEach(path -> {
                    totalCount.incrementAndGet();
                    String fileName = path.getFileName().toString();
                    // 移除可能的时间戳后缀（格式：baseName_timestamp.extension）
                    String originalFileName = fileName;
                    if (fileName.matches(".*_\\d{13}\\.[^.]+$")) {
                        originalFileName = fileName.replaceFirst("_(\\d{13})(\\.[^.]+)$", "$2");
                    }
                    
                    String mediaUrl = "/api/v1/uploads/contents/images/" + originalFileName;

                    try {
                        // 查询 content_media_urls 表获取 content_id
                        java.util.List<java.util.Map<String, Object>> results = jdbcTemplate.queryForList(
                            "SELECT content_id FROM content_media_urls WHERE media_url = ?",
                            mediaUrl
                        );

                        if (results.isEmpty()) {
                            notFoundCount.incrementAndGet();
                            log.warn("未找到对应的文章: fileName={}, mediaUrl={}", fileName, mediaUrl);
                        } else {
                            foundCount.incrementAndGet();
                            // 可能一个图片被多个文章引用
                            for (java.util.Map<String, Object> row : results) {
                                Object contentIdObj = row.get("content_id");
                                String contentId = contentIdObj != null ? contentIdObj.toString() : "null";
                                
                                // 查询文章详情（标题等）
                                try {
                                    java.util.Map<String, Object> contentInfo = jdbcTemplate.queryForMap(
                                        "SELECT id, title, url, published_at FROM contents WHERE id = ?::uuid",
                                        contentId
                                    );
                                    String title = (String) contentInfo.getOrDefault("title", "");
                                    String url = (String) contentInfo.getOrDefault("url", "");
                                    log.info("图片对应的文章: fileName={}, mediaUrl={}, contentId={}, title={}, url={}", 
                                            fileName, mediaUrl, contentId, title, url);
                                } catch (Exception e) {
                                    log.warn("查询文章详情失败: fileName={}, contentId={}, error={}", 
                                            fileName, contentId, e.getMessage());
                                    log.info("图片对应的文章 ID: fileName={}, mediaUrl={}, contentId={}", 
                                            fileName, mediaUrl, contentId);
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("查询图片对应的文章失败: fileName={}, mediaUrl={}, error={}", 
                                fileName, mediaUrl, e.getMessage());
                        notFoundCount.incrementAndGet();
                    }
                });
        }

        log.info("遍历完成，总图片数={}，找到对应文章={}，未找到对应文章={}",
                totalCount.get(), foundCount.get(), notFoundCount.get());
    }

    /**
     * 遍历 blank 目录下的图片，根据图片地址获取文章ID，然后通过文章的URL拉取内容获取postId，
     * 更新文章的图片和内容为拉取到的（文章内容为 postContent，图片内容为 extVO.extLiveVOS.img）。
     * 
     * 优化：如果多张图片被一个文章引用，则只加载一次；更新后删除旧的图片关联关系。
     */
    @Test
    void updateContentFromBlankImages() throws IOException {
        Path blankDir = Paths.get("..", "uploads", "contents", "blank").normalize();
        if (Files.notExists(blankDir) || !Files.isDirectory(blankDir)) {
            log.warn("blank 目录不存在或不是目录，跳过处理: {}", blankDir.toAbsolutePath());
            return;
        }

        // 获取 TimeStore 平台配置
        List<Platform> timeStorePlatforms = platformRepository.findAll().stream()
                .filter(p -> "TIMESTORE".equalsIgnoreCase(p.getType()))
                .toList();
        
        if (timeStorePlatforms.isEmpty()) {
            log.error("未找到 TimeStore 平台配置，无法执行更新");
            return;
        }
        
        Platform timeStorePlatform = timeStorePlatforms.get(0);
        Map<String, Object> config = parsePlatformConfig(timeStorePlatform.getConfig());
        log.info("使用 TimeStore 平台配置: name={}, baseUrl={}", timeStorePlatform.getName(), config.get("baseUrl"));

        log.info("开始处理 blank 目录下的图片，目录: {}", blankDir.toAbsolutePath());

        // 第一步：收集所有图片对应的 content_id，按 content_id 去重
        Map<UUID, List<String>> contentIdToImageFiles = new HashMap<>();
        AtomicInteger totalImageCount = new AtomicInteger();

        try (Stream<Path> stream = Files.list(blankDir)) {
            stream
                .filter(Files::isRegularFile)
                .filter(path -> isImageFile(path.getFileName().toString()))
                .forEach(path -> {
                    totalImageCount.incrementAndGet();
                    String fileName = path.getFileName().toString();
                    // 移除可能的时间戳后缀（格式：baseName_timestamp.extension）
                    String originalFileName = fileName;
                    if (fileName.matches(".*_\\d{13}\\.[^.]+$")) {
                        originalFileName = fileName.replaceFirst("_(\\d{13})(\\.[^.]+)$", "$2");
                    }
                    String mediaUrl = "/api/v1/uploads/contents/images/" + originalFileName;
                    
                    try {
                        List<UUID> contentIds = jdbcTemplate.queryForList(
                            "SELECT DISTINCT content_id FROM content_media_urls WHERE media_url = ?",
                            UUID.class,
                            mediaUrl
                        );
                        
                        for (UUID contentId : contentIds) {
                            contentIdToImageFiles.computeIfAbsent(contentId, k -> new ArrayList<>()).add(fileName);
                        }
                    } catch (Exception e) {
                        log.warn("查询图片对应的文章失败: fileName={}, mediaUrl={}, error={}", 
                                fileName, mediaUrl, e.getMessage());
                    }
                });
        }

        log.info("收集完成，总图片数={}，涉及文章数={}", totalImageCount.get(), contentIdToImageFiles.size());

        // 第二步：按 content_id 分组处理，每个文章只拉取一次
        AtomicInteger updatedCount = new AtomicInteger();
        AtomicInteger skippedCount = new AtomicInteger();
        AtomicInteger errorCount = new AtomicInteger();

        for (Map.Entry<UUID, List<String>> entry : contentIdToImageFiles.entrySet()) {
            UUID contentId = entry.getKey();
            List<String> imageFiles = entry.getValue();
            
            try {
                Content content = contentRepository.findById(contentId).orElse(null);
                if (content == null) {
                    log.warn("文章不存在: contentId={}, 关联图片数={}", contentId, imageFiles.size());
                    skippedCount.incrementAndGet();
                    continue;
                }
                
                // 从文章 URL 提取 postId
                String postId = extractTimeidFromUrl(content.getUrl());
                if (postId == null || postId.isEmpty()) {
                    log.warn("无法从文章 URL 提取 postId: url={}, contentId={}", content.getUrl(), contentId);
                    skippedCount.incrementAndGet();
                    continue;
                }
                
                log.info("处理文章: contentId={}, postId={}, 关联图片数={}, 图片文件={}", 
                        contentId, postId, imageFiles.size(), imageFiles);
                
                // 调用 TimeStore API 获取最新内容（每个文章只调用一次）
                Optional<PlatformContent> platformContentOpt = timeStoreAdapter.fetchContentByUrl(
                    content.getUrl(), 
                    config
                );
                
                if (platformContentOpt.isEmpty()) {
                    log.warn("无法拉取文章内容: postId={}, contentId={}", postId, contentId);
                    skippedCount.incrementAndGet();
                    continue;
                }
                
                PlatformContent platformContent = platformContentOpt.get();
                
                // 更新文章内容为 postContent
                content.setBody(platformContent.getBody());
                content.setTitle(platformContent.getTitle() != null ? platformContent.getTitle() : content.getTitle());
                
                // 下载图片并更新 mediaUrls
                List<String> newMediaUrls = new ArrayList<>();
                if (platformContent.getMediaUrls() != null && !platformContent.getMediaUrls().isEmpty()) {
                    for (String imageUrl : platformContent.getMediaUrls()) {
                        try {
                            String localImageUrl = contentAssetService.downloadImageAndSave(imageUrl);
                            if (localImageUrl != null && !localImageUrl.isEmpty()) {
                                newMediaUrls.add(localImageUrl);
                                log.info("已下载图片: 原url={}, 本地url={}", imageUrl, localImageUrl);
                            }
                        } catch (Exception e) {
                            log.warn("下载图片失败: url={}, error={}", imageUrl, e.getMessage());
                        }
                    }
                }
                
                // 先删除该文章的所有旧图片关联（解除当前图片和文章的关系）
                jdbcTemplate.update("DELETE FROM content_media_urls WHERE content_id = ?", contentId);
                log.info("已删除文章的所有旧图片关联: contentId={}", contentId);
                
                // 更新 mediaUrls（JPA 会自动插入新的关联）
                content.setMediaUrls(newMediaUrls.isEmpty() ? new ArrayList<>() : newMediaUrls);
                
                // 保存更新
                contentRepository.save(content);
                updatedCount.incrementAndGet();
                log.info("已更新文章: contentId={}, postId={}, 新图片数={}", contentId, postId, newMediaUrls.size());
                
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("处理文章失败: contentId={}, error={}", contentId, e.getMessage(), e);
            }
        }

        log.info("处理完成，总图片数={}，涉及文章数={}，已更新文章={}，跳过={}，处理失败={}",
                totalImageCount.get(), contentIdToImageFiles.size(), updatedCount.get(), skippedCount.get(), errorCount.get());
    }

    /**
     * 从 TimeStore URL 提取 timeid/postId。
     * URL 格式：https://web.timestore.vip/#/time/pages/timeDetail/index?timeid={postId}
     */
    private String extractTimeidFromUrl(String url) {
        if (url == null || url.isEmpty()) return null;
        int idx = url.indexOf("timeid=");
        if (idx < 0) return null;
        int start = idx + 7;
        int end = url.indexOf("&", start);
        if (end < 0) end = url.length();
        String s = url.substring(start, end).trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * 解析平台配置 JSON 字符串为 Map。
     */
    private Map<String, Object> parsePlatformConfig(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("解析平台配置失败: error={}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * 简单判断是否为图片扩展名。
     */
    private boolean isImageFile(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".png")
                || lower.endsWith(".gif")
                || lower.endsWith(".webp")
                || lower.endsWith(".bmp");
    }
}

