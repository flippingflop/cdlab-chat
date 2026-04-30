package com.cdlab.cdlabchat.connection;

import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.connection.dto.ConnectResponse;
import com.cdlab.cdlabchat.connection.dto.DisconnectResponse;
import com.cdlab.cdlabchat.event.Event;
import com.cdlab.cdlabchat.event.EventRepository;
import com.cdlab.cdlabchat.event.EventType;
import com.cdlab.cdlabchat.event.ServerEventEmitter;
import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.session.SessionRepository;
import com.cdlab.cdlabchat.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * connect / disconnect REST 트리거의 비즈니스 로직.
 * 운영 환경의 WebSocket open/close 핸들러에 대응되는 자리 — 본 프로젝트에서는
 * REST 트리거로 시뮬레이션한다 (design-decision.md 의 "웹소켓 미구현" 참조).
 *
 * 책임:
 *  - 세션/멤버/ENDED 가드
 *  - JOIN/RECONNECT 자동 분기 (events 조회로 (sessionId, userId, JOIN) row 존재 여부 확인)
 *  - server-emitted 이벤트 발행 (ServerEventEmitter)
 *  - MockSessionManager presence 토글
 *
 * EventService 와 분리한 이유 — JOIN/RECONNECT/DISCONNECT 는 client_event_id 멱등이 아니라
 * events row 존재 여부와 in-memory presence 로 분기되므로, EventService 의 client-emitted
 * 검증/멱등 파이프라인과 책임이 다르다.
 */
@Service
@RequiredArgsConstructor
public class ConnectionService {

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final ServerEventEmitter serverEventEmitter;
    private final MockSessionManager mockSessionManager;

    @Transactional
    public ConnectResponse connect(Long sessionId, User currentUser) {
        // 1) 세션/멤버/ENDED 가드
        Session session = loadAndGuard(sessionId, currentUser);

        // 2) JOIN row 존재 여부로 자동 분기.
        //    - (sessionId, userId, JOIN) row 부재 → 첫 connect 이므로 JOIN
        //    - 존재 → 후속 connect 이므로 RECONNECT
        //    이미 online 인 user 가 재호출해도 events 에 JOIN row 가 있어 RECONNECT 가 추가 발행됨 —
        //    실제 WebSocket 의 "재연결마다 이벤트" 동작과 일치.
        boolean hasJoined = eventRepository
                .existsBySessionIdAndUserIdAndEventType(sessionId, currentUser.getId(), EventType.JOIN);
        EventType type = hasJoined ? EventType.RECONNECT : EventType.JOIN;

        // 3) server-emit + in-memory presence 토글.
        //    payload 는 비워둠 — 향후 reason 등 추가 시 확장.
        Event emitted = serverEventEmitter.emit(session, currentUser, type, Map.of());
        mockSessionManager.connect(sessionId, currentUser.getId());

        return ConnectResponse.from(emitted);
    }

    @Transactional
    public DisconnectResponse disconnect(Long sessionId, User currentUser) {
        // 1) 세션/멤버/ENDED 가드
        Session session = loadAndGuard(sessionId, currentUser);

        // 2) 이미 offline 인 user 가 호출하면 no-op — events 에 stale DISCONNECT row 를 누적하지 않음.
        //    멱등성 보장 + "의미 있는 상태 전이만 events 에 기록" 원칙.
        if (!mockSessionManager.isOnline(sessionId, currentUser.getId())) {
            return DisconnectResponse.noop(sessionId, currentUser.getId());
        }

        // 3) DISCONNECT server-emit + presence 토글
        Event emitted = serverEventEmitter.emit(session, currentUser, EventType.DISCONNECT, Map.of());
        mockSessionManager.disconnect(sessionId, currentUser.getId());

        return DisconnectResponse.of(emitted);
    }

    private Session loadAndGuard(Long sessionId, User currentUser) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.isMember(currentUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_PARTICIPANT);
        }
        // ENDED 세션은 connect/disconnect 모두 거부.
        // "끝난 세션엔 누구도 online 아님" invariant 와 일관 (LeaveHandler 의 clearSession 과 짝).
        if (session.isEnded()) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }
        return session;
    }
}
