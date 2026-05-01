package com.cdlab.cdlabchat.session.timeline;

import com.cdlab.cdlabchat.event.Event;
import com.cdlab.cdlabchat.session.SessionStatus;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * events 시퀀스를 fold 하여 특정 시점 T 의 세션 상태를 재구성한다 (timeline 복원).
 * 순수 POJO — Spring 의존 없음, 단위테스트 용이.
 *
 * 결정성: events 가 immutable + append-only 이고, 입력으로 받는 events 가 created_at <= T 로 미리 필터되어 있다.
 * 따라서 같은 (sessionId, T) 에 대해 호출 시각과 무관하게 동일한 결과가 나온다.
 *
 * 1:1 채팅의 라이프사이클은 단순화되어 있어 status 는 SUSPENDED ↔ ENDED 두 값만 도달 가능.
 */
public final class SessionTimelineFolder {

    private SessionTimelineFolder() {}

    public static TimelineState fold(List<Event> events) {
        // LinkedHashMap → MESSAGE_CREATED 발생 순서(=createdAt 오름차순) 보존
        Map<Long, MessageState> messages = new LinkedHashMap<>();
        Lifecycle lifecycle = new Lifecycle();

        for (Event e : events) {
            switch (e.getEventType()) {
                case MESSAGE_CREATED -> applyCreated(e, messages);
                case MESSAGE_EDITED  -> applyEdited(e, messages);
                case MESSAGE_DELETED -> applyDeleted(e, messages);
                case LEAVE           -> applyLeave(e, lifecycle);
                case SESSION_ENDED   -> applyEnded(e, lifecycle);
                // JOIN/RECONNECT/DISCONNECT — connection lifetime(WebSocket 시뮬레이션) 의 휘발 정보.
                // 도메인 방 멤버십 fold 와 무관하므로 timeline 에서는 무시.
                default -> { }
            }
        }
        return new TimelineState(messages, lifecycle);
    }

    private static void applyCreated(Event e, Map<Long, MessageState> messages) {
        Long messageId = asLong(e.getPayload().get("messageId"));
        String content = (String) e.getPayload().get("content");
        messages.put(messageId, new MessageState(
                messageId, e.getUserId(), content, e.getCreatedAt(), null, null));
    }

    private static void applyEdited(Event e, Map<Long, MessageState> messages) {
        Long messageId = asLong(e.getPayload().get("messageId"));
        MessageState ms = messages.get(messageId);
        if (ms == null) return; // 누락된 CREATE — 안전하게 스킵 (정상 흐름에선 발생 불가)
        ms.content = (String) e.getPayload().get("content");
        ms.editedAt = e.getCreatedAt();
    }

    private static void applyDeleted(Event e, Map<Long, MessageState> messages) {
        Long messageId = asLong(e.getPayload().get("messageId"));
        MessageState ms = messages.get(messageId);
        if (ms == null) return;
        // 두 번째 DELETE (다른 client_event_id) 는 첫 deletedAt 유지 (도메인 softDelete 와 일관)
        if (ms.deletedAt == null) {
            ms.deletedAt = e.getCreatedAt();
        }
    }

    private static void applyLeave(Event e, Lifecycle lifecycle) {
        // 같은 user 의 두 번째 LEAVE 는 첫 시각 유지 — 도메인 Session.leave() 의 left_at 보존 정책과 일관.
        lifecycle.leftAtByUserId.putIfAbsent(e.getUserId(), e.getCreatedAt());
    }

    private static void applyEnded(Event e, Lifecycle lifecycle) {
        lifecycle.status = SessionStatus.ENDED;
        lifecycle.endedAt = e.getCreatedAt();
        Object triggeredBy = e.getPayload().get("triggeredByUserId");
        if (triggeredBy != null) {
            lifecycle.endedBy = asLong(triggeredBy);
        }
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        throw new IllegalStateException("expected numeric, got " + (o == null ? "null" : o.getClass()));
    }

    // === fold 결과 ===

    public static final class TimelineState {
        public final Map<Long, MessageState> messages;
        public final Lifecycle lifecycle;

        public TimelineState(Map<Long, MessageState> messages, Lifecycle lifecycle) {
            this.messages = messages;
            this.lifecycle = lifecycle;
        }
    }

    public static final class MessageState {
        public final Long id;
        public final Long userId;
        public String content;
        public final Instant createdAt;
        public Instant editedAt;
        public Instant deletedAt;

        public MessageState(Long id, Long userId, String content,
                            Instant createdAt, Instant editedAt, Instant deletedAt) {
            this.id = id;
            this.userId = userId;
            this.content = content;
            this.createdAt = createdAt;
            this.editedAt = editedAt;
            this.deletedAt = deletedAt;
        }
    }

    public static final class Lifecycle {
        public SessionStatus status = SessionStatus.SUSPENDED; // 초기값
        public Instant endedAt;
        public Long endedBy;
        // 시점 t 까지 LEAVE 한 user 들의 leftAt. 1:1 채팅이라 최대 2개 entry.
        // creator/joiner 매핑은 호출자(Service/DTO) 가 Session 도메인과 결합해서 처리 — folder 는 events 만 알면 충분.
        public final Map<Long, Instant> leftAtByUserId = new HashMap<>();
    }
}
