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
public class SaveEventResponse {

    private final Long eventId;
    private final Long sessionId;
    private final EventType eventType;
    private final Map<String, Object> payload;
    private final UUID clientEventId;
    private final Instant createdAt;

    /**
     * MESSAGE_CREATED 등 message 와 연관된 이벤트에서만 채워진다.
     * payload 에 enrich 된 messageId 를 응답 최상위로 끌어올려 클라이언트가
     * 후속 EDIT/DELETE 호출에 바로 사용할 수 있도록 한다.
     * 멱등 재시도 시에도 저장된 payload 에서 동일하게 추출되므로 일관성이 유지된다.
     */
    private final Long messageId;

    public static SaveEventResponse from(Event event) {
        return new SaveEventResponse(
                event.getId(),
                event.getSessionId(),
                event.getEventType(),
                event.getPayload(),
                event.getClientEventId(),
                event.getCreatedAt(),
                extractMessageId(event.getPayload())
        );
    }

    private static Long extractMessageId(Map<String, Object> payload) {
        if (payload == null) {
            return null;
        }
        Object raw = payload.get("messageId");
        if (raw instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
