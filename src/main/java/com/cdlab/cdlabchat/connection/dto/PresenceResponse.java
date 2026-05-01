package com.cdlab.cdlabchat.connection.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * GET /api/sessions/{id}/presence 응답.
 * MockSessionManager 의 in-memory presence 스냅샷을 그대로 노출한다.
 *
 * queriedAt 은 "이 응답은 호출 시점 스냅샷이며 휘발성" 임을 클라이언트에 명시하는 신호 —
 * 캐시 부적합. 운영 환경의 WebSocket subscribe(push) 가 본 GET 의 자리를 대체할 수 있다.
 */
@Getter
@AllArgsConstructor
public class PresenceResponse {

    private final Long sessionId;
    private final Long creatorId;
    private final Long joinerId;
    private final boolean creatorOnline;
    private final boolean joinerOnline;
    private final Instant queriedAt;
}
