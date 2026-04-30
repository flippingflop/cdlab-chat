package com.cdlab.cdlabchat.connection.dto;

import com.cdlab.cdlabchat.event.Event;
import com.cdlab.cdlabchat.event.EventType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * POST /api/sessions/{id}/disconnect 응답.
 * 이미 offline 인 사용자가 호출한 경우 noop=true, eventId/createdAt 은 null —
 * 멱등성을 위해 stale DISCONNECT 이벤트를 events 에 누적하지 않는다.
 */
@Getter
@AllArgsConstructor
public class DisconnectResponse {

    private final Long sessionId;
    private final Long userId;
    private final EventType eventType;
    private final Long eventId;
    private final Instant createdAt;
    private final boolean noop;

    public static DisconnectResponse of(Event event) {
        return new DisconnectResponse(
                event.getSessionId(),
                event.getUserId(),
                event.getEventType(),
                event.getId(),
                event.getCreatedAt(),
                false
        );
    }

    public static DisconnectResponse noop(Long sessionId, Long userId) {
        return new DisconnectResponse(
                sessionId,
                userId,
                EventType.DISCONNECT,
                null,
                null,
                true
        );
    }
}
