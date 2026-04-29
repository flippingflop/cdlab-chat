package com.cdlab.cdlabchat.event;

import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.event.dto.SaveEventRequest;
import com.cdlab.cdlabchat.event.dto.SaveEventResponse;
import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.session.SessionRepository;
import com.cdlab.cdlabchat.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SessionRepository sessionRepository;
    private final Map<EventType, EventHandler> handlers;

    public EventService(EventRepository eventRepository,
                        SessionRepository sessionRepository,
                        List<EventHandler> handlerBeans) {
        this.eventRepository = eventRepository;
        this.sessionRepository = sessionRepository;
        this.handlers = new EnumMap<>(EventType.class);
        for (EventHandler h : handlerBeans) {
            EventHandler prev = this.handlers.put(h.supports(), h);
            if (prev != null) {
                throw new IllegalStateException(
                        "duplicate EventHandler for type " + h.supports()
                                + ": " + prev.getClass() + " vs " + h.getClass());
            }
        }
    }

    @Transactional
    public SaveEventResponse saveEvent(Long sessionId, User currentUser, SaveEventRequest request) {
        // 1) 세션 조회 / 검증
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (session.isEnded()) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }

        if (!session.isMember(currentUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_PARTICIPANT);
        }

        // 2) client-emitted 만 허용. server-emitted (DISCONNECT/RECONNECT/SESSION_ENDED) 는 별도 진입점.
        EventType type = request.getEventType();
        if (!type.isClientEmitted()) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_EVENT_TYPE);
        }

        // 3) 멱등 선조회 — 이미 처리된 client_event_id 면 기존 이벤트 그대로 반환
        UUID clientEventId = request.getClientEventId();
        Optional<Event> existing = eventRepository.findByClientEventId(clientEventId);
        if (existing.isPresent()) {
            return SaveEventResponse.from(existing.get());
        }

        // 4) 핸들러 디스패치
        EventHandler handler = handlers.get(type);
        if (handler == null) {
            // 핸들러 null check
            throw new BusinessException(ErrorCode.UNSUPPORTED_EVENT_TYPE);
        }

        Map<String, Object> payload = request.getPayload() != null ? request.getPayload() : Map.of();

        // 5) 처리 + race 보루
        //    동시에 같은 client_event_id 가 들어와 선조회를 모두 통과한 경우, DB unique 가 한쪽을 막는다.
        try {
            Event saved = handler.handle(session, currentUser, payload, clientEventId);
            return SaveEventResponse.from(saved);
        } catch (DataIntegrityViolationException e) {
            Event found = eventRepository.findByClientEventId(clientEventId)
                    .orElseThrow(() -> e);
            return SaveEventResponse.from(found);
        }
    }
}
