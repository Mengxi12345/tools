package com.caat.exception;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {
    // 通用错误 1000-1999
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    // 业务错误 2000-2999
    PLATFORM_NOT_FOUND(2001, "平台不存在"),
    PLATFORM_ALREADY_EXISTS(2002, "平台已存在"),
    USER_NOT_FOUND(2003, "用户不存在"),
    USER_ALREADY_EXISTS(2004, "用户已存在"),
    CONTENT_NOT_FOUND(2005, "内容不存在"),
    FETCH_TASK_NOT_FOUND(2006, "刷新任务不存在"),
    FETCH_TASK_ALREADY_RUNNING(2007, "刷新任务正在运行中"),

    // 认证错误 3000-3999
    INVALID_TOKEN(3001, "无效的 Token"),
    TOKEN_EXPIRED(3002, "Token 已过期"),
    LOGIN_FAILED(3003, "登录失败，用户名或密码错误");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
