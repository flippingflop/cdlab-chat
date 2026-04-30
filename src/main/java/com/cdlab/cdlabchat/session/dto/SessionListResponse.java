package com.cdlab.cdlabchat.session.dto;

import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.session.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

/**
 * GET /api/sessions 응답 단건.
 * 상대방 정보는 ID 만 노출 (name 은 별도 user 조회 책임).
 * peer_left_at 등 상대 가시성 메타는 노출하지 않음 (다른 사용자의 행동 노출 회피).
 */
@Getter
@AllArgsConstructor
public class SessionListResponse {

    private final Long id;
    private final Long creatorId;
    private final Long joinerId;
    private final SessionStatus status;
    private final Instant createdAt;
    private final Instant endedAt;

    public static SessionListResponse from(Session session) {
        return new SessionListResponse(
                session.getId(),
                session.getCreatorId(),
                session.getJoinerId(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getEndedAt()
        );
    }
}
