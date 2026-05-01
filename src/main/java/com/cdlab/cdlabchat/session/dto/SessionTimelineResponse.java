package com.cdlab.cdlabchat.session.dto;

import com.cdlab.cdlabchat.session.SessionStatus;
import com.cdlab.cdlabchat.session.timeline.SessionTimelineFolder.TimelineState;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

/**
 * 시점 t 의 세션 상태 응답.
 *
 * leftAt 두 필드는 events 의 LEAVE row 를 fold 한 결과 — 도메인 방 멤버십(영속) 을 의미하며,
 * connection presence(휘발, MockSessionManager) 와는 별개의 개념이다.
 * 본 프로젝트의 JOIN 이벤트는 WebSocket 첫 connect 의미이며 방 입장과 무관 —
 * 1:1 채팅에서 방 입장은 세션 생성 자체이므로 timeline 의 멤버십 fold 는 LEAVE 만 본다.
 *
 * 같은 (sessionId, t) 호출은 항상 동일 응답 — events 가 immutable + append-only 이고
 * fold 입력이 created_at <= t 로 고정되기 때문 (결정성).
 */
@Getter
@AllArgsConstructor
public class SessionTimelineResponse {

    private final Long sessionId;
    private final Instant at;
    private final SessionStatus status;
    private final Instant endedAt;
    private final Long endedBy;
    private final Instant creatorLeftAt;
    private final Instant joinerLeftAt;
    private final List<MessageTimelineItem> messages;

    public static SessionTimelineResponse from(Long sessionId,
                                               Instant at,
                                               TimelineState state,
                                               Long creatorId,
                                               Long joinerId) {
        List<MessageTimelineItem> messages = state.messages.values().stream()
                .map(MessageTimelineItem::from)
                .toList();
        return new SessionTimelineResponse(
                sessionId,
                at,
                state.lifecycle.status,
                state.lifecycle.endedAt,
                state.lifecycle.endedBy,
                state.lifecycle.leftAtByUserId.get(creatorId),
                state.lifecycle.leftAtByUserId.get(joinerId),
                messages
        );
    }
}
