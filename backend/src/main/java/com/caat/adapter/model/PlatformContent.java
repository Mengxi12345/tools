package com.caat.adapter.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 平台内容模型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformContent {
    private String contentId;
    private String title;
    private String body;
    private String url;
    private ContentType contentType;
    private List<String> mediaUrls;
    private LocalDateTime publishedAt;
    private Map<String, Object> metadata; // 点赞数、转发数、评论数等
    private String authorId;
    private String authorUsername;

    public enum ContentType {
        TEXT, IMAGE, VIDEO, LINK
    }
}
