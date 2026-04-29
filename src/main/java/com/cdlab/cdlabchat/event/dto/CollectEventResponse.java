package com.cdlab.cdlabchat.event.dto;

import com.cdlab.cdlabchat.event.Event;
import com.cdlab.cdlabchat.event.EventType;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class CollectEventResponse {

    private final Long eventId;
    private final Long sessionId;
    private final EventType eventType;
    private final Map<String, Object> payload;
    private final UUID clientEventId;
    private final Instant createdAt;

    public static CollectEventResponse from(Event event) {
        return new CollectEventResponse(
                event.getId(),
                event.getSessionId(),
                event.getEventType(),
                event.getPayload(),
                event.getClientEventId(),
                event.getCreatedAt()
        );
    }
}
