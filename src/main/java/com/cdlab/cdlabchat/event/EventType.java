package com.cdlab.cdlabchat.event;

public enum EventType {
    MESSAGE_CREATED,
    MESSAGE_EDITED,
    MESSAGE_DELETED,
    JOIN,
    LEAVE,
    DISCONNECT,
    RECONNECT,
    SESSION_ENDED;

    /**
     * 클라이언트가 발행하는 이벤트인지 여부.
     * - true  → client_event_id 필수 (idempotency 키)
     * - false → server-emitted (DISCONNECT/RECONNECT/SESSION_ENDED), client_event_id NULL
     */
    public boolean isClientEmitted() {
        return switch (this) {
            case MESSAGE_CREATED, MESSAGE_EDITED, MESSAGE_DELETED, JOIN, LEAVE -> true;
            case DISCONNECT, RECONNECT, SESSION_ENDED -> false;
        };
    }

    /**
     * 종료된 세션에서도 받아들여지는 이벤트인지 여부.
     * - LEAVE: 한쪽이 먼저 나가 ENDED 가 된 세션에 다른 쪽이 뒤늦게 LEAVE 하는 경우를 허용해야 한다
     *   (자기 가시성을 정리할 수 있어야 자기 목록에서 사라짐).
     * - 그 외 client-emitted 는 종료된 세션에서 거부.
     */
    public boolean allowedOnEndedSession() {
        return this == LEAVE;
    }
}
