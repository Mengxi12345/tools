package com.caat.service;

import com.caat.entity.Content;
import com.caat.entity.Platform;
import com.caat.entity.TrackedUser;
import com.caat.repository.ContentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lowagie.text.Chunk;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfAction;
import com.lowagie.text.pdf.PdfDestination;
import com.lowagie.text.pdf.PdfOutline;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.SimpleBookmark;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.hibernate.Hibernate;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;

/**
 * PDF 与 Word 导出服务。
 * 支持按年/月/日组织、图片嵌入、日期排序。
 * 作者使用 username，文章内容在上方加粗，发布时间与查看原文同行且可点击。
 */
@Slf4j
@Service
public class PdfWordExportService {

    private final ContentRepository contentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public PdfWordExportService(ContentRepository contentRepository,
                                RestTemplate restTemplate,
                                ObjectMapper objectMapper) {
        this.contentRepository = contentRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    /** 单张图片最大尺寸（点） */
    private static final float IMG_SINGLE_MAX = 300f;
    /** 多张图片时每张最大尺寸（点），每行 3 张 */
    private static final float IMG_GRID_MAX = 140f;
    /** 图片压缩：最大宽度/高度（像素），超过则缩放 */
    private static final int MAX_IMAGE_DIMENSION = 1200;
    /** 图片压缩：JPEG 质量（0.0-1.0），0.7 表示70%质量 */
    private static final float JPEG_QUALITY = 0.75f;
    private static final DateTimeFormatter EXPORT_TIME_FMT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
    private static final DateTimeFormatter FILE_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern IMG_SRC_PATTERN = Pattern.compile("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);

    /** 常见中文字体路径（按优先级尝试）。优先 TTF，TTC 在 OpenPDF 中可能无法正确嵌入中文 */
    private static final String[] CHINESE_FONT_PATHS = {
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",  // macOS TTF，优先
            "/Library/Fonts/Arial Unicode.ttf",                      // macOS 符号链接
            "C:/Windows/Fonts/msyh.ttc",                             // Windows 微软雅黑 TTC
            "C:/Windows/Fonts/simsun.ttc",                           // Windows 宋体
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",           // Linux 文泉驿
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/System/Library/Fonts/PingFang.ttc",                     // macOS 苹方（TTC 可能有问题）
            "/System/Library/Fonts/Supplemental/Songti.ttc",           // macOS 宋体
    };

    public enum SortOrder { ASC, DESC }

    /** 导出结果：字节数据 + 建议下载文件名（username-平台-时间.ext） */
    public record ExportResult(byte[] data, String suggestedFileName) {}

    /** 进度回调：(进度0-100, 日志消息) */
    public interface ProgressCallback {
        void onProgress(int progress, String message);
    }

    /**
     * 导出为 PDF（带进度回调），返回 ExportResult 含建议文件名
     * 采用分批生成策略：每15天的文章生成一个临时PDF，最后合并成一个最终PDF
     */
    @Transactional(readOnly = true)
    public ExportResult exportToPdf(UUID userId, SortOrder sortOrder, ProgressCallback callback) throws IOException {
        if (callback != null) callback.onProgress(5, "开始加载文章...");
        log.info("开始加载用户文章: userId={}, sortOrder={}", userId, sortOrder);
        List<Content> contents = loadContentsWithPlatformAndUser(userId, sortOrder);
        log.info("文章加载完成: 共 {} 篇", contents.size());
        if (callback != null) callback.onProgress(15, "已加载 " + contents.size() + " 篇文章");
        if (contents.isEmpty()) {
            if (callback != null) callback.onProgress(100, "该用户暂无文章");
            return new ExportResult(new byte[0], null);
        }
        
        Content first = contents.get(0);
        String username = first.getUser() != null && first.getUser().getUsername() != null && !first.getUser().getUsername().isBlank()
                ? first.getUser().getUsername() : "未知";
        String platform = first.getPlatform() != null ? first.getPlatform().getName() : "未知";
        if (contents.stream().map(c -> c.getPlatform() != null ? c.getPlatform().getName() : "").distinct().count() > 1) {
            platform = "多平台";
        }
        String docTitle = username + "-" + platform + "-内容合集";
        String suggestedFileName = sanitizeFileName(username) + "-" + sanitizeFileName(platform) + "-" + LocalDateTime.now().format(FILE_TIME_FMT) + ".pdf";

        // 创建临时目录
        Path tempDir = Paths.get(uploadDir, "temp", "pdf_export_" + System.currentTimeMillis());
        Files.createDirectories(tempDir);
        List<Path> tempPdfFiles = new ArrayList<>();

        try {
            // 统计所有图片数量
            log.info("开始统计图片数量...");
            int totalImages = 0;
            for (Content c : contents) {
                totalImages += collectImageUrls(c).size();
            }
            log.info("图片统计完成: 共 {} 张图片", totalImages);
            if (callback != null && totalImages > 0) {
                callback.onProgress(16, "检测到 " + totalImages + " 张图片，开始处理...");
            } else if (callback != null) {
                callback.onProgress(16, "未检测到图片");
            }

            // 按日期排序
            contents.sort((a, b) -> {
                int cmp = a.getPublishedAt().compareTo(b.getPublishedAt());
                return sortOrder == SortOrder.DESC ? -cmp : cmp;
            });

            // 按15天分组生成临时PDF
            int daysPerBatch = 15;
            LocalDateTime batchStartDate = null;
            List<Content> currentBatch = new ArrayList<>();
            int batchIndex = 0;
            int total = contents.size();
            int processed = 0;

            for (Content c : contents) {
                LocalDateTime publishDate = c.getPublishedAt().toLocalDate().atStartOfDay();
                
                if (batchStartDate == null || 
                    java.time.temporal.ChronoUnit.DAYS.between(batchStartDate.toLocalDate(), publishDate.toLocalDate()) >= daysPerBatch) {
                    // 保存当前批次（如果有）
                    if (!currentBatch.isEmpty()) {
                        if (callback != null) {
                            callback.onProgress(18 + (int)(50.0 * batchIndex / Math.max(1, (total / 100 + 1))), 
                                "开始生成第 " + (batchIndex + 1) + " 个批次（" + currentBatch.size() + " 篇文章）...");
                        }
                        Path tempPdf = generateBatchPdf(currentBatch, docTitle, username, platform, contents.size(), 
                                batchIndex, tempDir, callback, processed, total, contents, sortOrder, totalImages);
                        tempPdfFiles.add(tempPdf);
                        processed += currentBatch.size();
                        batchIndex++;
                        if (callback != null) {
                            callback.onProgress(18 + (int)(50.0 * batchIndex / Math.max(1, (total / 100 + 1))), 
                                "第 " + batchIndex + " 个批次生成完成");
                        }
                    }
                    // 开始新批次
                    batchStartDate = publishDate;
                    currentBatch = new ArrayList<>();
                }
                currentBatch.add(c);
            }
            
            // 保存最后一个批次
            if (!currentBatch.isEmpty()) {
                if (callback != null) {
                    callback.onProgress(18 + (int)(50.0 * batchIndex / Math.max(1, (total / 100 + 1))), 
                        "开始生成第 " + (batchIndex + 1) + " 个批次（" + currentBatch.size() + " 篇文章）...");
                }
                Path tempPdf = generateBatchPdf(currentBatch, docTitle, username, platform, contents.size(), 
                        batchIndex, tempDir, callback, processed, total, contents, sortOrder, totalImages);
                tempPdfFiles.add(tempPdf);
                if (callback != null) {
                    callback.onProgress(18 + (int)(50.0 * (batchIndex + 1) / Math.max(1, (total / 100 + 1))), 
                        "第 " + (batchIndex + 1) + " 个批次生成完成");
                }
            }

            // 合并所有临时PDF
            if (callback != null) callback.onProgress(85, "正在合并 " + tempPdfFiles.size() + " 个PDF文件...");
            ByteArrayOutputStream finalBaos = mergePdfFiles(tempPdfFiles, callback);
            
            if (callback != null) callback.onProgress(100, "导出完成");
            return new ExportResult(finalBaos.toByteArray(), suggestedFileName);
        } finally {
            // 清理临时文件
            for (Path tempFile : tempPdfFiles) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("无法删除临时PDF文件: {}", tempFile, e);
                }
            }
            try {
                Files.deleteIfExists(tempDir);
            } catch (IOException e) {
                log.warn("无法删除临时目录: {}", tempDir, e);
            }
        }
    }

    /**
     * 生成一个批次的PDF文件（15天的文章）
     */
    private Path generateBatchPdf(List<Content> batchContents, String docTitle, String username, String platform,
                                   int totalArticles, int batchIndex, Path tempDir, ProgressCallback callback,
                                   int processedBefore, int total, List<Content> allContents, SortOrder sortOrder, int totalImages) throws IOException {
        log.info("开始生成第 {} 个批次PDF: 包含 {} 篇文章", batchIndex + 1, batchContents.size());
        com.lowagie.text.Document document = new com.lowagie.text.Document(com.lowagie.text.PageSize.A4);
        Path tempPdfFile = tempDir.resolve("batch_" + batchIndex + ".pdf");
        PdfWriter writer = PdfWriter.getInstance(document, new java.io.FileOutputStream(tempPdfFile.toFile()));
        writer.setViewerPreferences(PdfWriter.PageModeUseOutlines);
        document.open();

        Font titleFont = createChineseFont(18, Font.BOLD);
        Font headingFont = createChineseFont(14, Font.BOLD);
        Font yearFont = createChineseFont(14, Font.BOLD);
        Font monthFont = createChineseFont(12, Font.BOLD);
        Font dayFont = createChineseFont(10, Font.BOLD);
        Font normalFont = createChineseFont(10, Font.NORMAL);
        Font smallFont = createChineseFont(8, Font.NORMAL);

        // 只在第一个批次添加标题和统计信息
        if (batchIndex == 0) {
            document.add(new Paragraph(docTitle, titleFont));
            document.add(new Paragraph(" "));
            document.add(new Paragraph("导出时间：" + LocalDateTime.now().format(EXPORT_TIME_FMT), normalFont));
            document.add(new Paragraph("导出文章总数：" + totalArticles, normalFont));
            document.add(new Paragraph(" "));

            // 统计所有文章
            Map<Integer, Long> yearCounts = allContents.stream()
                    .collect(Collectors.groupingBy(c -> c.getPublishedAt().getYear(), Collectors.counting()));
            Map<String, Long> monthCounts = allContents.stream()
                    .collect(Collectors.groupingBy(c -> c.getPublishedAt().getYear() + "-" + String.format("%02d", c.getPublishedAt().getMonthValue()), Collectors.counting()));

            document.add(new Paragraph("各年份文章总数：", headingFont));
            yearCounts.entrySet().stream().sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                    .forEach(e -> document.add(new Paragraph("  " + e.getKey() + " 年：" + e.getValue() + " 篇", normalFont)));
            document.add(new Paragraph("各月份文章总数：", headingFont));
            monthCounts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                    .forEach(e -> document.add(new Paragraph("  " + e.getKey() + "：" + e.getValue() + " 篇", normalFont)));
            document.add(new Paragraph(" "));
        }

        Map<Integer, Map<Integer, Map<Integer, List<Content>>>> grouped = groupByYearMonthDay(batchContents);
        var yearOrder = grouped.keySet().stream().sorted(sortOrder == SortOrder.DESC ? Comparator.reverseOrder() : Comparator.naturalOrder()).toList();

        int processed = processedBefore;
        for (Integer year : yearOrder) {
            Paragraph yearPara = new Paragraph(year + " 年", yearFont);
            document.add(yearPara);
            PdfDestination yearDest = new PdfDestination(PdfDestination.FITH, writer.getVerticalPosition(true));
            PdfOutline yearOutline = new PdfOutline(writer.getRootOutline(), yearDest, year + " 年");

            Map<Integer, Map<Integer, List<Content>>> months = grouped.get(year);
            for (Integer month : months.keySet().stream().sorted(sortOrder == SortOrder.DESC ? Comparator.reverseOrder() : Comparator.naturalOrder()).toList()) {
                document.add(new Paragraph(month + " 月", monthFont));
                PdfDestination monthDest = new PdfDestination(PdfDestination.FITH, writer.getVerticalPosition(true));
                PdfOutline monthOutline = new PdfOutline(yearOutline, monthDest, month + " 月");

                Map<Integer, List<Content>> days = months.get(month);
                for (Integer day : days.keySet().stream().sorted(sortOrder == SortOrder.DESC ? Comparator.reverseOrder() : Comparator.naturalOrder()).toList()) {
                    List<Content> dayContents = days.get(day);
                    String dayTitle = year + "年" + month + "月" + day + "日（共 " + dayContents.size() + " 篇）";
                    document.add(new Paragraph(dayTitle, dayFont));
                    PdfDestination dayDest = new PdfDestination(PdfDestination.FITH, writer.getVerticalPosition(true));
                    new PdfOutline(monthOutline, dayDest, dayTitle);
                    java.util.concurrent.atomic.AtomicInteger imageCounter = new java.util.concurrent.atomic.AtomicInteger(0);
                    for (Content c : dayContents) {
                        addContentToPdf(document, c, normalFont, smallFont, imageCounter, callback);
                        processed++;
                        // 每写入100篇文章显示一条日志
                        if (callback != null && processed % 100 == 0) {
                            int pct = 20 + (int) (60.0 * processed / total);
                            callback.onProgress(Math.min(pct, 80), "已写入 " + processed + "/" + total + " 篇（批次 " + (batchIndex + 1) + "）");
                        }
                    }
                }
            }
        }

        document.close();
        log.info("第 {} 个批次PDF生成完成: 文件大小 {} 字节", batchIndex + 1, Files.size(tempPdfFile));
        return tempPdfFile;
    }

    /**
     * 合并多个PDF文件，保留各批次的目录书签并设置打开时显示目录导航栏
     */
    private ByteArrayOutputStream mergePdfFiles(List<Path> pdfFiles, ProgressCallback callback) throws IOException {
        log.info("开始合并PDF文件: 共 {} 个文件", pdfFiles.size());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        com.lowagie.text.Document document = new com.lowagie.text.Document();
        PdfCopy copy = new PdfCopy(document, baos);
        document.open();

        // 设置打开 PDF 时默认显示左侧目录导航栏（书签面板）
        copy.setViewerPreferences(PdfWriter.PageModeUseOutlines);

        List<Map<String, Object>> allOutlines = new ArrayList<>();
        int totalFiles = pdfFiles.size();
        int totalPages = 0;

        for (int i = 0; i < pdfFiles.size(); i++) {
            Path pdfFile = pdfFiles.get(i);
            log.debug("合并第 {} 个PDF文件: {}", i + 1, pdfFile.getFileName());
            PdfReader reader = new PdfReader(pdfFile.toFile().getAbsolutePath());
            int pages = reader.getNumberOfPages();

            // 提取当前 PDF 的书签，并按合并后的页码偏移
            List<Map<String, Object>> bookmarks = SimpleBookmark.getBookmarkList(reader);
            if (bookmarks != null && !bookmarks.isEmpty()) {
                SimpleBookmark.shiftPageNumbersInRange(bookmarks, totalPages, null);
                allOutlines.addAll(bookmarks);
            }

            totalPages += pages;
            log.debug("第 {} 个PDF文件包含 {} 页", i + 1, pages);
            for (int page = 1; page <= pages; page++) {
                copy.addPage(copy.getImportedPage(reader, page));
            }
            copy.freeReader(reader);
            reader.close();

            if (callback != null) {
                int pct = 85 + (int) (10.0 * (i + 1) / totalFiles);
                callback.onProgress(Math.min(pct, 99), "已合并 " + (i + 1) + "/" + totalFiles + " 个PDF文件（共 " + totalPages + " 页）");
            }
        }

        // 将合并后的书签写入最终 PDF
        if (!allOutlines.isEmpty()) {
            copy.setOutlines(allOutlines);
        }

        document.close();
        log.info("PDF合并完成: 共 {} 个文件，{} 页，书签数 {}，最终大小 {} 字节", totalFiles, totalPages, allOutlines.size(), baos.size());
        return baos;
    }

    private void addContentToPdf(com.lowagie.text.Document document, Content c, com.lowagie.text.Font normalFont, com.lowagie.text.Font smallFont) throws com.lowagie.text.DocumentException {
        addContentToPdf(document, c, normalFont, smallFont, null, null);
    }

    private void addContentToPdf(com.lowagie.text.Document document, Content c, com.lowagie.text.Font normalFont, com.lowagie.text.Font smallFont, 
                                 java.util.concurrent.atomic.AtomicInteger imageCounter, ProgressCallback callback) throws com.lowagie.text.DocumentException {
        document.add(new Paragraph(" ", normalFont));
        String body = stripHtml(c.getBody());
        if (body != null && !body.isEmpty()) {
            document.add(new Paragraph(body, normalFont));
        }

        List<String> imageUrls = collectImageUrls(c);
        if (!imageUrls.isEmpty()) {
            float imgSize = imageUrls.size() > 1 ? IMG_GRID_MAX : IMG_SINGLE_MAX;
            if (imageUrls.size() > 1) {
                PdfPTable table = new PdfPTable(3);
                table.setWidthPercentage(100f);
                table.setSpacingBefore(4f);
                table.setSpacingAfter(4f);
                int added = 0;
                for (String url : imageUrls) {
                    try {
                        // 直接读取本地文件或下载远程图片，不使用缓存
                        byte[] imgBytes = fetchImageBytes(url, imageCounter, callback);
                        if (imgBytes != null && imgBytes.length > 0) {
                            com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(imgBytes);
                            img.scaleToFit(imgSize, imgSize);
                            PdfPCell cell = new PdfPCell(img, true);
                            cell.setPadding(2f);
                            cell.setBorder(com.lowagie.text.Rectangle.NO_BORDER);
                            table.addCell(cell);
                            added++;
                        }
                    } catch (Exception e) {
                        log.warn("无法加载图片: {}", url, e);
                    }
                }
                int remainder = (3 - added % 3) % 3;
                for (int i = 0; i < remainder; i++) {
                    table.addCell(new PdfPCell());
                }
                document.add(table);
            } else {
                try {
                    // 直接读取本地文件或下载远程图片，不使用缓存
                    byte[] imgBytes = fetchImageBytes(imageUrls.get(0), imageCounter, callback);
                    if (imgBytes != null && imgBytes.length > 0) {
                        com.lowagie.text.Image img = com.lowagie.text.Image.getInstance(imgBytes);
                        img.scaleToFit(imgSize, imgSize);
                        document.add(img);
                    }
                } catch (Exception e) {
                    log.warn("无法加载图片: {}", imageUrls.get(0), e);
                }
            }
        }

        String timeStr = c.getPublishedAt().format(DATE_FMT);
        String linkUrl = c.getUrl() != null ? c.getUrl() : "";
        Paragraph metaPara = new Paragraph();
        metaPara.add(new Chunk("发布时间：" + timeStr + "  ", smallFont));
        Chunk linkChunk = new Chunk("查看原文", smallFont);
        linkChunk.setUnderline(0.1f, -2f);
        linkChunk.setAction(new PdfAction(linkUrl));
        metaPara.add(linkChunk);
        document.add(metaPara);
        LineSeparator line = new LineSeparator();
        line.setPercentage(95f);
        document.add(new com.lowagie.text.Chunk(line));
    }

    private String sanitizeFileName(String s) {
        if (s == null) return "unknown";
        return s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    /** 创建支持中文的字体，失败时回退到 Helvetica（中文会显示为空白） */
    private Font createChineseFont(int size, int style) {
        for (String path : CHINESE_FONT_PATHS) {
            try {
                java.io.File f = new java.io.File(path);
                if (!f.exists()) continue;
                String fontKey = path.endsWith(".ttc") ? path + ",0" : path;
                BaseFont bf = BaseFont.createFont(fontKey, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
                log.info("PDF 中文字体已加载: {}", path);
                return new Font(bf, size, style);
            } catch (Exception e) {
                log.trace("无法加载字体 {}: {}", path, e.getMessage());
            }
        }
        try {
            java.io.InputStream is = getClass().getResourceAsStream("/fonts/NotoSansSC-Regular.ttf");
            if (is != null) {
                byte[] fontBytes = is.readAllBytes();
                BaseFont bf = BaseFont.createFont("NotoSansSC-Regular.ttf", BaseFont.IDENTITY_H,
                        BaseFont.EMBEDDED, true, fontBytes, null);
                log.info("PDF 中文字体已从 resources/fonts 加载");
                return new Font(bf, size, style);
            }
        } catch (Exception e) {
            log.trace("无法从 resources 加载字体: {}", e.getMessage());
        }
        log.warn("未找到中文字体，PDF 中文将显示为空白。请将 NotoSansSC-Regular.ttf 放入 src/main/resources/fonts/");
        return new Font(Font.HELVETICA, size, style);
    }

    /**
     * 导出为 Word (docx)（带进度回调），返回 ExportResult 含建议文件名
     */
    @Transactional(readOnly = true)
    public ExportResult exportToWord(UUID userId, SortOrder sortOrder, ProgressCallback callback) throws IOException {
        if (callback != null) callback.onProgress(5, "开始加载文章...");
        List<Content> contents = loadContentsWithPlatformAndUser(userId, sortOrder);
        if (callback != null) callback.onProgress(15, "已加载 " + contents.size() + " 篇文章");
        if (contents.isEmpty()) {
            if (callback != null) callback.onProgress(100, "该用户暂无文章");
            return new ExportResult(new byte[0], null);
        }
        
        if (callback != null) callback.onProgress(20, "正在生成 Word 文档...");
        Content first = contents.get(0);
        String username = first.getUser() != null && first.getUser().getUsername() != null && !first.getUser().getUsername().isBlank()
                ? first.getUser().getUsername() : "未知";
        String platform = first.getPlatform() != null ? first.getPlatform().getName() : "未知";
        if (contents.stream().map(c -> c.getPlatform() != null ? c.getPlatform().getName() : "").distinct().count() > 1) {
            platform = "多平台";
        }
        String docTitle = username + "-" + platform + "-内容合集";
        String suggestedFileName = sanitizeFileName(username) + "-" + sanitizeFileName(platform) + "-" + LocalDateTime.now().format(FILE_TIME_FMT) + ".docx";

        org.apache.poi.xwpf.usermodel.XWPFDocument document = new org.apache.poi.xwpf.usermodel.XWPFDocument();
        org.apache.poi.xwpf.usermodel.XWPFParagraph p;
        org.apache.poi.xwpf.usermodel.XWPFRun r;

        p = document.createParagraph();
        r = p.createRun();
        r.setText(docTitle);
        r.setBold(true);
        r.setFontSize(18);
        p = document.createParagraph();
        p.createRun().setText("");
        p = document.createParagraph();
        p.createRun().setText("导出时间：" + LocalDateTime.now().format(EXPORT_TIME_FMT));
        p = document.createParagraph();
        p.createRun().setText("导出文章总数：" + contents.size());
        p = document.createParagraph();
        p.createRun().setText("");

        Map<Integer, Long> yearCounts = contents.stream().collect(Collectors.groupingBy(c -> c.getPublishedAt().getYear(), Collectors.counting()));
        Map<String, Long> monthCounts = contents.stream().collect(Collectors.groupingBy(c -> c.getPublishedAt().getYear() + "-" + String.format("%02d", c.getPublishedAt().getMonthValue()), Collectors.counting()));

        p = document.createParagraph();
        r = p.createRun();
        r.setText("各年份文章总数：");
        r.setBold(true);
        r.setFontSize(12);
        yearCounts.entrySet().stream().sorted(Map.Entry.<Integer, Long>comparingByKey().reversed())
                .forEach(e -> document.createParagraph().createRun().setText("  " + e.getKey() + " 年：" + e.getValue() + " 篇"));
        p = document.createParagraph();
        r = p.createRun();
        r.setText("各月份文章总数：");
        r.setBold(true);
        r.setFontSize(12);
        monthCounts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByKey().reversed())
                .forEach(e -> document.createParagraph().createRun().setText("  " + e.getKey() + "：" + e.getValue() + " 篇"));
        p = document.createParagraph();
        p.createRun().setText("");

        Map<Integer, Map<Integer, Map<Integer, List<Content>>>> grouped = groupByYearMonthDay(contents);

        int total = contents.size();
        int[] processed = {0};
        for (Integer year : grouped.keySet().stream().sorted(sortOrder == SortOrder.DESC ? Comparator.reverseOrder() : Comparator.naturalOrder()).toList()) {
            p = document.createParagraph();
            p.setStyle("Heading1");
            r = p.createRun();
            r.setText(year + " 年");
            r.setBold(true);
            r.setFontSize(14);
            Map<Integer, Map<Integer, List<Content>>> months = grouped.get(year);
            for (Integer month : months.keySet().stream().sorted(sortOrder == SortOrder.DESC ? Comparator.reverseOrder() : Comparator.naturalOrder()).toList()) {
                p = document.createParagraph();
                p.setStyle("Heading2");
                r = p.createRun();
                r.setText(month + " 月");
                r.setBold(true);
                r.setFontSize(12);
                Map<Integer, List<Content>> days = months.get(month);
                for (Integer day : days.keySet().stream().sorted(sortOrder == SortOrder.DESC ? Comparator.reverseOrder() : Comparator.naturalOrder()).toList()) {
                    List<Content> dayContents = days.get(day);
                    p = document.createParagraph();
                    p.setStyle("Heading3");
                    r = p.createRun();
                    r.setText(year + "年" + month + "月" + day + "日（共 " + dayContents.size() + " 篇）");
                    r.setBold(true);
                    r.setFontSize(10);
                    for (Content c : dayContents) {
                        addContentToWord(document, c);
                        processed[0]++;
                        // 每写入100篇文章显示一条日志
                        if (callback != null && processed[0] % 100 == 0) {
                            int pct = 20 + (int) (60.0 * processed[0] / total);
                            callback.onProgress(Math.min(pct, 85), "已写入 " + processed[0] + "/" + total + " 篇");
                        }
                    }
                }
            }
        }

        if (callback != null) callback.onProgress(90, "正在保存文件...");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        document.write(baos);
        document.close();
        if (callback != null) callback.onProgress(100, "导出完成");
        return new ExportResult(baos.toByteArray(), suggestedFileName);
    }

    private void addContentToWord(org.apache.poi.xwpf.usermodel.XWPFDocument document, Content c) {
        org.apache.poi.xwpf.usermodel.XWPFParagraph p;
        org.apache.poi.xwpf.usermodel.XWPFRun r;

        String body = stripHtml(c.getBody());
        if (body != null && !body.isEmpty()) {
            p = document.createParagraph();
            r = p.createRun();
            r.setText(body);
            r.setFontSize(11);
        }

        List<String> imageUrls = collectImageUrls(c);
        if (!imageUrls.isEmpty()) {
            int imgEmu = imageUrls.size() > 1 ? (int) (140 * 9525L) : (int) (300 * 9525L);
            if (imageUrls.size() > 1) {
                int rows = (imageUrls.size() + 2) / 3;
                org.apache.poi.xwpf.usermodel.XWPFTable table = document.createTable(rows, 3);
                table.setWidth("100%");
                int idx = 0;
                for (String url : imageUrls) {
                    try {
                        // 直接读取本地文件或下载远程图片，不使用缓存
                        byte[] imgBytes = fetchImageBytes(url);
                        if (imgBytes != null && imgBytes.length > 0) {
                            int rowIdx = idx / 3;
                            int colIdx = idx % 3;
                            org.apache.poi.xwpf.usermodel.XWPFTableCell cell = table.getRow(rowIdx).getCell(colIdx);
                            org.apache.poi.xwpf.usermodel.XWPFParagraph cellPara = cell.getParagraphs().isEmpty() ? cell.addParagraph() : cell.getParagraphs().get(0);
                            cellPara.setAlignment(org.apache.poi.xwpf.usermodel.ParagraphAlignment.CENTER);
                            org.apache.poi.xwpf.usermodel.XWPFRun cellRun = cellPara.createRun();
                            int picType = org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
                            if (imgBytes.length >= 2 && (imgBytes[0] & 0xFF) == 0xFF && (imgBytes[1] & 0xFF) == 0xD8) {
                                picType = org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG;
                            }
                            cellRun.addPicture(new java.io.ByteArrayInputStream(imgBytes), picType, "img", imgEmu, imgEmu);
                            idx++;
                        }
                    } catch (Exception e) {
                        log.warn("无法加载图片: {}", url, e);
                    }
                }
            } else {
                try {
                    // 直接读取本地文件或下载远程图片，不使用缓存
                    byte[] imgBytes = fetchImageBytes(imageUrls.get(0));
                    if (imgBytes != null && imgBytes.length > 0) {
                        p = document.createParagraph();
                        r = p.createRun();
                        int picType = org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG;
                        if (imgBytes.length >= 2 && (imgBytes[0] & 0xFF) == 0xFF && (imgBytes[1] & 0xFF) == 0xD8) {
                            picType = org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_JPEG;
                        }
                        r.addPicture(new java.io.ByteArrayInputStream(imgBytes), picType, "img", imgEmu, imgEmu);
                    }
                } catch (Exception e) {
                    log.warn("无法加载图片: {}", imageUrls.get(0), e);
                }
            }
        }

        p = document.createParagraph();
        String timeStr = c.getPublishedAt().format(DATE_FMT);
        r = p.createRun();
        r.setText("发布时间：" + timeStr + " ");
        r.setFontSize(8);
        org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun linkRun = p.createHyperlinkRun(c.getUrl() != null ? c.getUrl() : "");
        linkRun.setText("查看原文");
        linkRun.setFontSize(8);
        linkRun.setUnderline(org.apache.poi.xwpf.usermodel.UnderlinePatterns.SINGLE);
        document.createParagraph().createRun().setText("────────────────────────────────────────────────────────────────");
    }

    @Transactional(readOnly = true)
    protected List<Content> loadContentsWithPlatformAndUser(UUID userId, SortOrder sortOrder) {
        List<Content> all = new ArrayList<>();
        int page = 0;
        int size = 500;
        Sort sort = sortOrder == SortOrder.DESC
                ? Sort.by(Sort.Direction.DESC, "publishedAt")
                : Sort.by(Sort.Direction.ASC, "publishedAt");
        log.info("开始分页加载文章: userId={}, pageSize={}, sortOrder={}", userId, size, sortOrder);
        while (true) {
            Pageable pageable = PageRequest.of(page, size, sort);
            var result = contentRepository.findByUserIdWithPlatformAndUser(userId, pageable);
            List<Content> pageContents = result.getContent();
            log.debug("加载第 {} 页: 获取到 {} 篇文章", page + 1, pageContents.size());
            // 在同一个事务中初始化延迟加载的 mediaUrls 集合
            // 必须在事务内访问，否则会触发 LazyInitializationException
            for (Content c : pageContents) {
                // 使用 Hibernate.initialize() 强制初始化延迟加载的集合
                Hibernate.initialize(c.getMediaUrls());
            }
            all.addAll(pageContents);
            if (!result.hasNext()) break;
            page++;
        }
        log.info("文章加载完成: 共 {} 页，{} 篇文章", page + 1, all.size());
        return all;
    }

    private Map<Integer, Map<Integer, Map<Integer, List<Content>>>> groupByYearMonthDay(List<Content> contents) {
        Map<Integer, Map<Integer, Map<Integer, List<Content>>>> result = new LinkedHashMap<>();
        for (Content c : contents) {
            int y = c.getPublishedAt().getYear();
            int m = c.getPublishedAt().getMonthValue();
            int d = c.getPublishedAt().getDayOfMonth();
            result.computeIfAbsent(y, k -> new LinkedHashMap<>())
                    .computeIfAbsent(m, k -> new LinkedHashMap<>())
                    .computeIfAbsent(d, k -> new ArrayList<>())
                    .add(c);
        }
        return result;
    }

    private List<String> collectImageUrls(Content c) {
        List<String> urls = new ArrayList<>();
        if (c.getMediaUrls() != null) {
            urls.addAll(c.getMediaUrls());
        }
        String body = c.getBody();
        if (body != null) {
            Matcher m = IMG_SRC_PATTERN.matcher(body);
            while (m.find()) {
                String src = m.group(1).trim();
                if (!src.isEmpty() && !urls.contains(src)) {
                    urls.add(src);
                }
            }
        }
        return urls;
    }

    private byte[] fetchImageBytes(String url) throws IOException {
        return fetchImageBytes(url, null, null);
    }

    private byte[] fetchImageBytes(String url, java.util.concurrent.atomic.AtomicInteger imageCounter, ProgressCallback callback) throws IOException {
        if (url == null || url.isEmpty()) return null;
        byte[] originalBytes = null;
        
        if (url.startsWith("/api/v1/uploads/")) {
            String relPath = url.substring("/api/v1/uploads/".length());
            Path filePath = Paths.get(uploadDir, relPath).normalize();
            if (Files.exists(filePath) && Files.isReadable(filePath)) {
                originalBytes = Files.readAllBytes(filePath);
            }
        } else if (url.startsWith("http://") || url.startsWith("https://")) {
            try {
                originalBytes = restTemplate.getForObject(URI.create(url), byte[].class);
            } catch (Exception e) {
                log.debug("无法通过 HTTP 获取图片: {}", url, e);
                return null;
            }
        }
        
        if (originalBytes == null || originalBytes.length == 0) {
            return null;
        }
        
        // 压缩图片以减小内存占用
        return compressImage(originalBytes, imageCounter, callback);
    }
    
    /**
     * 压缩图片：如果图片尺寸超过限制，则缩放并压缩
     * @param originalBytes 原始图片字节数组
     * @return 压缩后的图片字节数组（JPEG格式）
     */
    private byte[] compressImage(byte[] originalBytes) {
        return compressImage(originalBytes, null, null);
    }

    /**
     * 压缩图片：如果图片尺寸超过限制，则缩放并压缩
     * @param originalBytes 原始图片字节数组
     * @param imageCounter 图片计数器（用于统计和日志）
     * @param callback 进度回调（用于更新进度）
     * @return 压缩后的图片字节数组（JPEG格式）
     */
    private byte[] compressImage(byte[] originalBytes, java.util.concurrent.atomic.AtomicInteger imageCounter, ProgressCallback callback) {
        if (originalBytes == null || originalBytes.length == 0) {
            return originalBytes;
        }
        
        try {
            BufferedImage originalImage = ImageIO.read(new java.io.ByteArrayInputStream(originalBytes));
            if (originalImage == null) {
                if (imageCounter != null) imageCounter.incrementAndGet();
                return originalBytes; // 无法解析，返回原数据
            }
            
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            // 如果图片尺寸在限制内，直接返回（避免不必要的压缩）
            if (originalWidth <= MAX_IMAGE_DIMENSION && originalHeight <= MAX_IMAGE_DIMENSION) {
                if (imageCounter != null) {
                    int count = imageCounter.incrementAndGet();
                    // 每100张图片打印一次进度
                    if (count % 100 == 0 && callback != null) {
                        callback.onProgress(17, "已处理 " + count + " 张图片");
                    }
                }
                return originalBytes;
            }
            
            // 计算缩放比例
            double scale = Math.min(
                (double) MAX_IMAGE_DIMENSION / originalWidth,
                (double) MAX_IMAGE_DIMENSION / originalHeight
            );
            int newWidth = (int) (originalWidth * scale);
            int newHeight = (int) (originalHeight * scale);
            
            // 缩放图片
            BufferedImage scaledImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2d = scaledImage.createGraphics();
            g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
            g2d.dispose();
            
            // 转换为JPEG格式并压缩
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaledImage, "jpg", baos);
            byte[] compressed = baos.toByteArray();
            
            // 即使压缩后文件大小没有明显减小，也返回压缩后的图片
            // 因为内存占用主要取决于像素数（width * height），而不是文件大小
            // 缩放后的图片像素数更少，内存占用更小
            if (imageCounter != null) {
                int count = imageCounter.incrementAndGet();
                // 每100张图片打印一次进度
                if (count % 100 == 0 && callback != null) {
                    callback.onProgress(17, "已处理 " + count + " 张图片");
                }
            }
            
            return compressed;
        } catch (Exception e) {
            log.debug("图片压缩失败，使用原图: {}", e.getMessage());
            if (imageCounter != null) imageCounter.incrementAndGet();
            return originalBytes; // 压缩失败，返回原数据
        }
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&nbsp;", " ")
                .replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">")
                .replaceAll("&amp;", "&")
                .replaceAll("&quot;", "\"")
                .replaceAll("[ \t]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n+", "\n")
                .trim();
        return s;
    }
}
