package com.cdlab.cdlabchat.session.timeline;

import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.event.Event;
import com.cdlab.cdlabchat.event.EventRepository;
import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.session.SessionRepository;
import com.cdlab.cdlabchat.session.dto.SessionTimelineResponse;
import com.cdlab.cdlabchat.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 특정 시점 T 의 세션 상태(timeline)를 events fold 로 재구성한다.
 * 결정성: 같은 (sessionId, T) 는 호출 시각과 무관하게 동일한 응답.
 * 근거 — events 가 immutable + append-only, 입력 부분집합은 created_at <= T 로 고정.
 *
 * 본 구현은 매 요청마다 events 전체를 fold 한다 (snapshot/projection 캐싱 없음).
 * 가산점 항목인 "Snapshot 생성/갱신" 은 별도 기능 — 중간 materialized 상태를 저장하여
 * fold 비용을 줄이는 최적화로, 본 timeline API 의 외부 계약과는 독립적.
 */
@Service
@RequiredArgsConstructor
public class SessionTimelineService {

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;

    @Transactional(readOnly = true)
    public SessionTimelineResponse timeline(Long sessionId, User currentUser, Instant at) {
        // 1) 세션 조회 / 멤버 검증 — 종료된 세션도 허용 (히스토리 조회 정책과 일관)
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        if (!session.isMember(currentUser.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_PARTICIPANT);
        }

        // 2) at 검증 — 세션 생성 이전을 조회해도 유효한 이벤트가 없으므로 명시적 거부.
        //    미래 시각은 허용 (현재 상태와 동등, 결정성 속성도 자연스럽게 만족).
        if (at.isBefore(session.getCreatedAt())) {
            throw new BusinessException(ErrorCode.TIMELINE_AT_BEFORE_SESSION_CREATED);
        }

        // 3) events 시점 부분집합 조회 — idx_events_session_created 가 그대로 커버
        List<Event> events = eventRepository
                .findBySessionIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc(sessionId, at);

        // 4) fold → DTO. creator/joiner ID 는 LEAVE fold 결과를 응답의 두 leftAt 필드로 평탄화하기 위해 전달.
        //    folder 는 events 만 알고 도메인 Session 을 모르므로, 매핑 책임은 여기서.
        SessionTimelineFolder.TimelineState state = SessionTimelineFolder.fold(events);
        return SessionTimelineResponse.from(
                sessionId, at, state, session.getCreatorId(), session.getJoinerId());
    }
}
