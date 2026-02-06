package com.caat.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.time.LocalDateTime;

/**
 * Elasticsearch 内容文档
 */
@Document(indexName = "contents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContentDocument {
    @Id
    private String id;
    
    @Field(type = FieldType.Keyword)
    private String platformId;
    
    @Field(type = FieldType.Keyword)
    private String userId;
    
    @Field(type = FieldType.Text)
    private String title;
    
    @Field(type = FieldType.Text)
    private String body;
    
    @Field(type = FieldType.Keyword)
    private String url;
    
    @Field(type = FieldType.Keyword)
    private String contentType;
    
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd||epoch_millis")
    private LocalDateTime publishedAt;
    
    @Field(type = FieldType.Boolean)
    private Boolean isRead;
    
    @Field(type = FieldType.Boolean)
    private Boolean isFavorite;
    
    @Field(type = FieldType.Date, pattern = "yyyy-MM-dd'T'HH:mm:ss||yyyy-MM-dd||epoch_millis")
    private LocalDateTime createdAt;
}
