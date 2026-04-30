package com.cdlab.cdlabchat.session.dto;

import com.cdlab.cdlabchat.message.dto.MessageResponse;
import com.cdlab.cdlabchat.session.timeline.SessionTimelineFolder.MessageState;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;

@Getter
@AllArgsConstructor
public class MessageTimelineItem {

    private final Long id;
    private final Long userId;
    private final String content;
    private final Instant createdAt;
    private final Instant editedAt;
    private final Instant deletedAt;

    public static MessageTimelineItem from(MessageState ms) {
        // 마스킹 정책은 메시지 조회 API 와 일관 (placeholder 상수 재사용)
        String content = ms.deletedAt != null ? MessageResponse.DELETED_PLACEHOLDER : ms.content;
        return new MessageTimelineItem(
                ms.id, ms.userId, content, ms.createdAt, ms.editedAt, ms.deletedAt);
    }
}
