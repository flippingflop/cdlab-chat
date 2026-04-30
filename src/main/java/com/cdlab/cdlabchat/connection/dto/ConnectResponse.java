package com.cdlab.cdlabchat.connection.dto;

import com.cdlab.cdlabchat.event.Event;
import com.cdlab.cdlabchat.event.EventType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * POST /api/sessions/{id}/connect 응답.
 * eventType 은 서버가 events 조회로 자동 분기한 결과 — JOIN(첫 connect) 또는 RECONNECT(후속).
 */
@Getter
@AllArgsConstructor
public class ConnectResponse {

    private final Long sessionId;
    private final Long userId;
    private final EventType eventType;
    private final Long eventId;
    private final Instant createdAt;

    public static ConnectResponse from(Event event) {
        return new ConnectResponse(
                event.getSessionId(),
                event.getUserId(),
                event.getEventType(),
                event.getId(),
                event.getCreatedAt()
        );
    }
}
