package com.caat.adapter.exception;

import com.caat.exception.ErrorCode;
import lombok.Getter;

/**
 * 平台适配器异常
 */
@Getter
public class PlatformException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String platformType;

    public PlatformException(String platformType, ErrorCode errorCode, String message) {
        super(message);
        this.platformType = platformType;
        this.errorCode = errorCode;
    }

    public PlatformException(String platformType, ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.platformType = platformType;
        this.errorCode = errorCode;
    }
}
