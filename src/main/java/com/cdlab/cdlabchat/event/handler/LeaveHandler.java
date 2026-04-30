package com.cdlab.cdlabchat.event.handler;

import com.cdlab.cdlabchat.event.Event;
import com.cdlab.cdlabchat.event.EventHandler;
import com.cdlab.cdlabchat.event.EventRepository;
import com.cdlab.cdlabchat.event.EventType;
import com.cdlab.cdlabchat.event.ServerEventEmitter;
import com.cdlab.cdlabchat.connection.MockSessionManager;
import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * LEAVE 처리 핸들러.
 * - sessions: 호출자 쪽 left_at 세팅 + (필요 시) status=ENDED 전이
 * - events: LEAVE INSERT
 * - LEAVE 호출로 처음 ENDED 로 전이된 경우에 한해 SESSION_ENDED 를 server-emit
 *
 * EventService 의 @Transactional 안에서 호출되어 위 INSERT 들이 한 트랜잭션으로 묶인다.
 */
@Component
@RequiredArgsConstructor
public class LeaveHandler implements EventHandler {

    private final EventRepository eventRepository;
    private final ServerEventEmitter serverEventEmitter;
    private final MockSessionManager mockSessionManager;

    @Override
    public EventType supports() {
        return EventType.LEAVE;
    }

    @Override
    public Event handle(Session session,
                        User currentUser,
                        Map<String, Object> payload,
                        UUID clientEventId) {
        // 1) 전이 감지를 위해 호출 전 상태 캡처
        boolean wasEndedBefore = session.isEnded();

        // 2) 도메인 전이 — 호출자 left_at 세팅 + (미종료였다면) status=ENDED
        session.leave(currentUser.getId());

        // 3) LEAVE 이벤트 저장. payload 는 추가 데이터 없음 (멱등은 client_event_id 로).
        Event leaveEvent = eventRepository.save(new Event(
                session.getId(),
                currentUser.getId(),
                EventType.LEAVE,
                Map.of(),
                clientEventId));

        // 4) LEAVE 로 인해 막 ENDED 로 전이된 경우에만 SESSION_ENDED 를 server-emit.
        //    이미 ENDED 였던 세션에 두 번째 LEAVE 가 들어온 경우엔 재발행하지 않음 (라이프사이클 신호 중복 방지).
        //    SESSION_ENDED 발행과 함께 presence(MockSessionManager) bucket 을 정리해
        //    "끝난 세션엔 누구도 online 아님" invariant 를 보장한다.
        if (!wasEndedBefore && session.isEnded()) {
            Map<String, Object> sessionEndedPayload = Map.of(
                    "triggeredByUserId", currentUser.getId(),
                    "reason", "LEAVE"
            );
            serverEventEmitter.emit(session, currentUser, EventType.SESSION_ENDED, sessionEndedPayload);
            mockSessionManager.clearSession(session.getId());
        }

        return leaveEvent;
    }
}
