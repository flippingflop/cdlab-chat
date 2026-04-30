package com.cdlab.cdlabchat.session.dto;

import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.session.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * POST /api/sessions/{id}/end 응답.
 * 호출 후 세션 상태(status, endedAt) 와 호출자의 leftAt 을 함께 노출.
 */
@Getter
@AllArgsConstructor
public class EndSessionResponse {

    private final Long sessionId;
    private final SessionStatus status;
    private final Instant endedAt;
    /**
     * 호출자(=요청 사용자) 본인의 leftAt.
     * 상대방의 leftAt 은 노출하지 않음 (타 사용자 행동 정보 회피).
     */
    private final Instant leftAt;

    public static EndSessionResponse from(Session session, Long callerUserId) {
        Instant callerLeftAt = session.getCreatorId().equals(callerUserId)
                ? session.getCreatorLeftAt()
                : session.getJoinerLeftAt();
        return new EndSessionResponse(
                session.getId(),
                session.getStatus(),
                session.getEndedAt(),
                callerLeftAt
        );
    }
}
