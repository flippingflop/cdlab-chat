package com.cdlab.cdlabchat.event.handler;

import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.event.Event;
import com.cdlab.cdlabchat.event.EventHandler;
import com.cdlab.cdlabchat.event.EventRepository;
import com.cdlab.cdlabchat.event.EventType;
import com.cdlab.cdlabchat.message.Message;
import com.cdlab.cdlabchat.message.MessageRepository;
import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * MESSAGE_CREATED 처리 핸들러.
 * - messages 프로젝션에 새 row INSERT
 * - events 권위 로그에 INSERT (server-enriched payload: content + messageId)
 *
 * EventService 의 @Transactional 안에서 호출되므로 두 INSERT 는 같은 트랜잭션에 묶인다.
 * client_event_id race 시 EventService 가 DataIntegrityViolationException 을 잡아 재조회하므로,
 * 본 핸들러는 race 처리를 직접 하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class MessageCreatedHandler implements EventHandler {

    private final MessageRepository messageRepository;
    private final EventRepository eventRepository;

    @Override
    public EventType supports() {
        return EventType.MESSAGE_CREATED;
    }

    @Override
    public Event handle(Session session,
                        User currentUser,
                        Map<String, Object> payload,
                        UUID clientEventId) {
        // 1) payload 검증 — content 필수, 공백/빈 문자열 불가
        String content = extractContent(payload);

        // 2) projection INSERT — id 확보
        Message message = messageRepository.save(
                new Message(session.getId(), currentUser.getId(), content));

        // 3) events INSERT — 클라이언트 의도(content) + 처리 결과(messageId) 를 함께 저장
        Map<String, Object> storedPayload = Map.of(
                "content", content,
                "messageId", message.getId()
        );
        Event event = new Event(
                session.getId(),
                currentUser.getId(),
                EventType.MESSAGE_CREATED,
                storedPayload,
                clientEventId);
        return eventRepository.save(event);
    }

    private String extractContent(Map<String, Object> payload) {
        Object raw = payload.get("content");
        if (!(raw instanceof String content) || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_EVENT_PAYLOAD);
        }
        return content;
    }
}
