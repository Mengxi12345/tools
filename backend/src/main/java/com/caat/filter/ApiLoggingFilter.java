package com.caat.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 统一打印接口入参、出参（DEBUG 级别）。
 * 关键存储、下发数据由各 Service 内打 DEBUG 日志。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiLoggingFilter extends OncePerRequestFilter {

    @Value("${app.api-logging.enabled:true}")
    private boolean enabled;

    @Value("${app.api-logging.max-body-length:2048}")
    private int maxBodyLength;

    /** 不打印请求/响应体的路径（敏感或二进制） */
    private static final String[] SKIP_BODY_PATTERNS = { "/api/v1/auth/", "/api/v1/uploads/" };

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith("/api/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String query = request.getQueryString();
        boolean skipBody = shouldSkipBody(uri);

        ContentCachingRequestWrapper requestWrapper = request instanceof ContentCachingRequestWrapper
                ? (ContentCachingRequestWrapper) request
                : new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            int status = responseWrapper.getStatus();
            String requestBody = skipBody ? "(skip)" : getRequestBody(requestWrapper);
            String responseBody = skipBody ? "(skip)" : getResponseBody(responseWrapper);
            if (log.isDebugEnabled()) {
                log.debug("API 入参  {} {} {} query={} body={}", method, uri, status, query, truncate(requestBody, maxBodyLength));
                log.debug("API 出参  {} {} status={} body={}", method, uri, status, truncate(responseBody, maxBodyLength));
            }
            responseWrapper.copyBodyToResponse();
        }
    }

    private static boolean shouldSkipBody(String uri) {
        if (uri == null) return true;
        for (String p : SKIP_BODY_PATTERNS) {
            if (uri.startsWith(p)) return true;
        }
        return false;
    }

    private static String getRequestBody(ContentCachingRequestWrapper request) {
        byte[] buf = request.getContentAsByteArray();
        if (buf == null || buf.length == 0) return "";
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static String getResponseBody(ContentCachingResponseWrapper response) {
        byte[] buf = response.getContentAsByteArray();
        if (buf == null || buf.length == 0) return "";
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...(truncated " + (s.length() - max) + ")";
    }
}
