package com.cdlab.cdlabchat.session.dto;

import com.cdlab.cdlabchat.session.SessionStatus;
import com.cdlab.cdlabchat.session.timeline.SessionTimelineFolder.TimelineState;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.List;

@Getter
@AllArgsConstructor
public class SessionTimelineResponse {

    private final Long sessionId;
    private final Instant at;
    private final SessionStatus status;
    private final Instant endedAt;
    private final Long endedBy;
    private final List<MessageTimelineItem> messages;

    public static SessionTimelineResponse from(Long sessionId, Instant at, TimelineState state) {
        List<MessageTimelineItem> messages = state.messages.values().stream()
                .map(MessageTimelineItem::from)
                .toList();
        return new SessionTimelineResponse(
                sessionId,
                at,
                state.lifecycle.status,
                state.lifecycle.endedAt,
                state.lifecycle.endedBy,
                messages
        );
    }
}
