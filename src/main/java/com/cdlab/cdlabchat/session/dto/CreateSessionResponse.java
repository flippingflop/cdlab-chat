package com.cdlab.cdlabchat.session.dto;

import com.cdlab.cdlabchat.session.Session;
import com.cdlab.cdlabchat.session.SessionStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class CreateSessionResponse {

    private final Long id;
    private final Long creatorId;
    private final Long joinerId;
    private final SessionStatus status;
    private final Instant createdAt;

    public static CreateSessionResponse from(Session session) {
        return new CreateSessionResponse(
                session.getId(),
                session.getCreatorId(),
                session.getJoinerId(),
                session.getStatus(),
                session.getCreatedAt()
        );
    }
}
