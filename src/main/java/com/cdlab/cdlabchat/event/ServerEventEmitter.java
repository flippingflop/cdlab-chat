package com.cdlab.cdlabchat.event;

import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * server-emitted 이벤트 발행 진입점.
 * - DISCONNECT/RECONNECT/SESSION_ENDED 등 서버가 자체 판단으로 발행하는 이벤트.
 * - client_event_id 는 항상 NULL (멱등 키 없음 — 서버가 중복 발행을 스스로 방지해야 함).
 * - 호출자(ex. LeaveHandler) 의 @Transactional 안에서 호출되는 것을 전제로 한다.
 *
 * EventService 와 분리한 이유:
 * 1) EventService 는 핸들러 디스패치 / client-emitted 검증 / clientEventId 멱등을 책임.
 *    server-emitted 는 그 어느 것도 필요 없어 책임이 다름.
 * 2) EventService → handler → EventService 의 순환 의존을 회피.
 *
 * 향후 발행 지점이 1개(LEAVE → SESSION_ENDED) 를 넘어 늘어나면 별도 핸들러 디스패치 도입을 검토.
 */
@Component
@RequiredArgsConstructor
public class ServerEventEmitter {

    private final EventRepository eventRepository;

    public Event emit(Session session, User actor, EventType type, Map<String, Object> payload) {
        if (type.isClientEmitted()) {
            throw new IllegalArgumentException(
                    "client-emitted type cannot be emitted via ServerEventEmitter: " + type);
        }
        return eventRepository.save(new Event(
                session.getId(),
                actor.getId(),
                type,
                payload,
                null));
    }
}
