package com.cdlab.cdlabchat.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    SELF_SESSION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신과의 세션은 생성할 수 없습니다."),
    SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일한 두 사용자 사이의 진행 중인 세션이 이미 존재합니다.");

    private final HttpStatus httpStatus;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
