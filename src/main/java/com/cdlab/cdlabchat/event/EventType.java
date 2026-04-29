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
}
