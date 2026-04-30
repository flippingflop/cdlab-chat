package com.cdlab.cdlabchat.message.dto;

import com.cdlab.cdlabchat.message.Message;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class MessageResponse {

    public static final String DELETED_PLACEHOLDER = "삭제된 메시지입니다.";

    private final Long id;
    private final Long sessionId;
    private final Long userId;
    private final String content;
    private final Instant createdAt;
    private final Instant editedAt;
    private final Instant deletedAt;

    public static MessageResponse from(Message message) {
        // 삭제된 메시지는 placeholder 로 마스킹 — 위치/순서/작성자는 보존, 원문만 가린다
        String content = message.isDeleted() ? DELETED_PLACEHOLDER : message.getContent();
        return new MessageResponse(
                message.getId(),
                message.getSessionId(),
                message.getUserId(),
                content,
                message.getCreatedAt(),
                message.getEditedAt(),
                message.getDeletedAt()
        );
    }
}
