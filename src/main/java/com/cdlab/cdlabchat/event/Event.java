package com.cdlab.cdlabchat.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * append-only, immutable. 변경 메서드 없음.
 */
@Entity
@Table(name = "events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    /**
     * Hibernate 6 의 native JSONB 매핑.
     * 추가 라이브러리 없이 Map ↔ JSONB 자동 변환.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> payload;

    /**
     * 클라이언트 발급 idempotency 키.
     * server-emitted 이벤트는 NULL.
     */
    @Column(name = "client_event_id", unique = true)
    private UUID clientEventId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Event(Long sessionId,
                 Long userId,
                 EventType eventType,
                 Map<String, Object> payload,
                 UUID clientEventId) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.eventType = eventType;
        this.payload = payload;
        this.clientEventId = clientEventId;
    }
}
