package com.cdlab.cdlabchat.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    SELF_SESSION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자기 자신과의 세션은 생성할 수 없습니다."),
    SESSION_ALREADY_EXISTS(HttpStatus.CONFLICT, "동일한 두 사용자 사이의 진행 중인 세션이 이미 존재합니다."),

    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 세션입니다."),
    SESSION_ENDED(HttpStatus.CONFLICT, "이미 종료된 세션입니다."),
    FORBIDDEN_PARTICIPANT(HttpStatus.FORBIDDEN, "세션 참여자만 접근할 수 있습니다."),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 메시지입니다."),
    MESSAGE_ALREADY_DELETED(HttpStatus.CONFLICT, "이미 삭제된 메시지입니다."),
    FORBIDDEN_MESSAGE_OWNER(HttpStatus.FORBIDDEN, "본인이 작성한 메시지만 수정/삭제할 수 있습니다."),
    INVALID_EVENT_PAYLOAD(HttpStatus.BAD_REQUEST, "이벤트 payload 가 올바르지 않습니다."),
    UNSUPPORTED_EVENT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이벤트 타입입니다.");

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
