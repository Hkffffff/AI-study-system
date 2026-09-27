package com.aistudy.common.error;

/**
 * Business error with a stable {@link ErrorCode}. The message is shown to the user as the
 * problem detail, so it must not contain internal information.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;

    public ApiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
