package com.cdlab.cdlabchat.connection;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 채점자 검증 트리거 전용 — 사용자의 채팅 연결 상태를 in-memory 로 시뮬레이션한다.
 * 실제 운영에서는 WebSocket session registry 가 동일 역할을 수행할 자리이며,
 * 본 클래스는 REST 검증 환경에서 대체하기 위한 mock.
 *
 * 본 클래스의 의도/한계는 design-decision.md 의 "웹소켓 미구현" 항목을 따른다.
 *
 * 휘발성 — 앱 재시작 시 모든 상태가 초기화된다. 이는 실제 WebSocket 연결이 서버 재시작 시
 * 일제히 끊어지는 동작과 일치하므로 시뮬레이션 충실성 측면에서 의도된 동작.
 *
 * 입자도 — sessionId 별로 online userIds 를 분리 보관한다. 같은 사용자가 여러 세션에
 * 동시에 connect 한 상황을 자연스럽게 표현하며, 세션 단위 정리(clearSession) 가 가능하다.
 * sessionId bucket 은 첫 connect 시점에 lazy 생성되므로, 채점자가 임의 sessionId 로
 * 호출해도 추가 사전준비 없이 동작한다.
 *
 * 명명 주의 — 본 클래스의 "session" 은 WebSocket connection lifetime 을 가리키며,
 * 본 프로젝트의 채팅 도메인 Session 엔티티와는 무관하다. connection 패키지로 분리해
 * 도메인과 시뮬레이션 책임을 코드 단에서 구분한다.
 */
@Component
public class MockSessionManager {

    // 단일 인스턴스 전제. 외부 Map 은 ConcurrentHashMap, 내부 Set 은 ConcurrentHashMap.newKeySet 으로
    // session 단위 동시 connect/disconnect 를 안전하게 처리.
    private final Map<Long, Set<Long>> onlineBySession = new ConcurrentHashMap<>();

    public void connect(Long sessionId, Long userId) {
        onlineBySession
                .computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet())
                .add(userId);
    }

    public void disconnect(Long sessionId, Long userId) {
        Set<Long> bucket = onlineBySession.get(sessionId);
        if (bucket != null) {
            bucket.remove(userId);
        }
    }

    public boolean isOnline(Long sessionId, Long userId) {
        Set<Long> bucket = onlineBySession.get(sessionId);
        return bucket != null && bucket.contains(userId);
    }

    /**
     * SESSION_ENDED 가 발행된 세션의 presence 정보를 일괄 제거한다.
     * "끝난 세션엔 누구도 online 아님" invariant 를 보장하며, 운영 환경의 WebSocket
     * server 가 SESSION_ENDED 시 active connection 을 정리/close 하는 동작을 모사한다.
     */
    public void clearSession(Long sessionId) {
        onlineBySession.remove(sessionId);
    }
}
