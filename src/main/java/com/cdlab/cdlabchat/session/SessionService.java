package com.cdlab.cdlabchat.session;

import com.cdlab.cdlabchat.common.exception.BusinessException;
import com.cdlab.cdlabchat.common.exception.ErrorCode;
import com.cdlab.cdlabchat.event.EventService;
import com.cdlab.cdlabchat.event.EventType;
import com.cdlab.cdlabchat.event.dto.SaveEventRequest;
import com.cdlab.cdlabchat.session.dto.EndSessionResponse;
import com.cdlab.cdlabchat.session.dto.SessionListResponse;
import com.cdlab.cdlabchat.user.User;
import com.cdlab.cdlabchat.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final EventService eventService;

    @Transactional
    public Session create(User creator, Long joinerId) {
        // 1) 자기 자신과의 세션 차단 — DB CHECK 와 이중 방어
        if (creator.getId().equals(joinerId)) {
            throw new BusinessException(ErrorCode.SELF_SESSION_NOT_ALLOWED);
        }

        // 2) joiner 가 실제 존재하는 사용자인지 확인
        if (!userRepository.existsById(joinerId)) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3) 1쌍 1세션 사전 체크 — 같은 두 사용자(순서 무관) 사이의 진행 중 세션이 있으면 차단
        sessionRepository.findActiveBetween(creator.getId(), joinerId)
                .ifPresent(s -> { throw new BusinessException(ErrorCode.SESSION_ALREADY_EXISTS); });

        // 4) 신규 세션 저장
        //    DB partial unique (LEAST/GREATEST) 가 동시 요청 race 의 최종 보루.
        //    사전 체크를 통과한 두 요청이 동시에 들어와도 DB 에서 한쪽은 실패한다.
        try {
            return sessionRepository.save(new Session(creator.getId(), joinerId));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.SESSION_ALREADY_EXISTS);
        }
    }

    /**
     * 현재 사용자에게 보이는 세션 목록.
     * - 자신이 명시적으로 LEAVE 한 세션은 제외 (left_at 필터)
     * - ENDED 세션도 left_at 이 NULL 이면 히스토리로 포함
     * - created_at DESC 정렬
     */
    @Transactional(readOnly = true)
    public List<SessionListResponse> list(User currentUser) {
        return sessionRepository.findVisibleByUserOrderByCreatedAtDesc(currentUser.getId()).stream()
                .map(SessionListResponse::from)
                .toList();
    }

    /**
     * 세션 종료(=호출자의 명시적 LEAVE).
     * 내부적으로 LEAVE 이벤트를 EventService 를 통해 발행 — 검증/멱등/핸들러 디스패치 재사용.
     * LeaveHandler 가 도메인 전이 + (필요시) SESSION_ENDED server-emit 을 수행.
     */
    @Transactional
    public EndSessionResponse end(Long sessionId, User currentUser, UUID clientEventId) {
        // 1) LEAVE 이벤트 발행을 EventService 에 위임 (세션/멤버 검증, 멱등 모두 위임).
        SaveEventRequest req = new SaveEventRequest();
        req.setEventType(EventType.LEAVE);
        req.setPayload(Map.of());
        req.setClientEventId(clientEventId);
        eventService.saveEvent(sessionId, currentUser, req);

        // 2) 갱신된 세션 상태 재조회 후 응답 변환.
        //    같은 트랜잭션이므로 1차 캐시에서 동일 인스턴스 반환 — 도메인 전이가 이미 반영되어 있음.
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
        return EndSessionResponse.from(session, currentUser.getId());
    }
}
