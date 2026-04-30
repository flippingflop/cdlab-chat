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
     * - false → server-emitted, client_event_id NULL
     *
     * JOIN/RECONNECT 도 server-emitted —
     *   트리거가 connect REST(WebSocket 시뮬레이션) 이고, 클라이언트가 명시 발행하지 않는다.
     *   서버가 events 조회로 (sessionId, userId, JOIN) row 존재 여부에 따라 자동 분기 발행한다.
     *   멱등은 client_event_id 가 아니라 events 자체의 row 존재 여부로 자연 분기됨.
     */
    public boolean isClientEmitted() {
        return switch (this) {
            case MESSAGE_CREATED, MESSAGE_EDITED, MESSAGE_DELETED, LEAVE -> true;
            case JOIN, RECONNECT, DISCONNECT, SESSION_ENDED -> false;
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
