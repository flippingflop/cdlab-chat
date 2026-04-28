package com.cdlab.cdlabchat.common.response;

import com.cdlab.cdlabchat.common.exception.ErrorCode;

import java.time.Instant;

public record ErrorResponse(
        String code,
        String message,
        Instant timestamp
) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), Instant.now());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.name(), message, Instant.now());
    }
}
