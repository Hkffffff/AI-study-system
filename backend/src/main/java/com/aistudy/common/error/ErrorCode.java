package com.aistudy.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Stable, machine-readable error codes returned in the {@code code} property of every
 * {@link org.springframework.http.ProblemDetail}. Modules add their own codes here
 * (see docs/04-backend-modules.md §6).
 */
public enum ErrorCode {

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "请求不合法"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "参数校验失败"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支持的请求方法"),
    CONFLICT(HttpStatus.CONFLICT, "资源状态冲突"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支持的内容类型"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误");

    private final HttpStatus status;
    private final String title;

    ErrorCode(HttpStatus status, String title) {
        this.status = status;
        this.title = title;
    }

    public HttpStatus status() {
        return status;
    }

    public String title() {
        return title;
    }

    /** Generic code for errors raised by Spring MVC itself (no business code available). */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        for (ErrorCode code : values()) {
            if (code.status.value() == status.value()) {
                return code;
            }
        }
        return status.is5xxServerError() ? INTERNAL_ERROR : BAD_REQUEST;
    }
}
