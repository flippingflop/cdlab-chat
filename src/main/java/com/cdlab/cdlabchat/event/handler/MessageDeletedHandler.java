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
 * MESSAGE_DELETED 처리 핸들러.
 * - messages 프로젝션 soft delete (deleted_at 세팅)
 * - events 권위 로그에 INSERT
 *
 * 검증: 동일 세션 소속 / 본인 작성.
 * 이미 삭제된 메시지에 대한 재호출은 softDelete() 가 no-op 이라 사실상 멱등하지만,
 * 동일 client_event_id 재시도가 아닌 새 client_event_id 로 들어오면 events 에는 새 row 가 쌓인다
 * (멱등 보장 단위는 client_event_id).
 */
@Component
@RequiredArgsConstructor
public class MessageDeletedHandler implements EventHandler {

    private final MessageRepository messageRepository;
    private final EventRepository eventRepository;

    @Override
    public EventType supports() {
        return EventType.MESSAGE_DELETED;
    }

    @Override
    public Event handle(Session session,
                        User currentUser,
                        Map<String, Object> payload,
                        UUID clientEventId) {
        // 1) payload 검증 — messageId 필수
        Long messageId = extractMessageId(payload);

        // 2) 메시지 조회 + 소속/작성자 검증
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MESSAGE_NOT_FOUND));

        if (!message.getSessionId().equals(session.getId())) {
            // 다른 세션의 메시지 → 노출 회피 차원에서 NOT_FOUND 로 응답
            throw new BusinessException(ErrorCode.MESSAGE_NOT_FOUND);
        }
        if (!message.getUserId().equals(currentUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_MESSAGE_OWNER);
        }

        // 3) projection soft delete — 이미 삭제 상태면 no-op
        message.softDelete();

        // 4) events INSERT — messageId 만 저장
        Map<String, Object> storedPayload = Map.of(
                "messageId", message.getId()
        );
        Event event = new Event(
                session.getId(),
                currentUser.getId(),
                EventType.MESSAGE_DELETED,
                storedPayload,
                clientEventId);
        return eventRepository.save(event);
    }

    private Long extractMessageId(Map<String, Object> payload) {
        Object raw = payload.get("messageId");
        if (raw instanceof Number n) {
            return n.longValue();
        }
        throw new BusinessException(ErrorCode.INVALID_EVENT_PAYLOAD);
    }
}
