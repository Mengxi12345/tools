package com.caat.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 刷新请求
 * 支持 ISO-8601 字符串（含 Z），前端 toISOString() 可直接使用
 */
@Data
public class FetchRequest {
    private LocalDateTime startTime; // 可选，不传则使用最后拉取时间
    private LocalDateTime endTime;   // 可选，不传则使用当前时间
    /** 拉取速度：fast=快速（单页少量），normal=完整（多页） */
    private String fetchMode;        // "fast" | "normal"，默认 "normal"

    @JsonSetter("startTime")
    public void setStartTimeFromString(String value) {
        this.startTime = parseDateTime(value);
    }

    @JsonSetter("endTime")
    public void setEndTimeFromString(String value) {
        this.endTime = parseDateTime(value);
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            if (value.contains("Z") || value.contains("+") || value.matches(".*-\\d{2}:\\d{2}$")) {
                return LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault());
            }
            return LocalDateTime.parse(value.replace("Z", ""));
        } catch (Exception e) {
            return null;
        }
    }
}
