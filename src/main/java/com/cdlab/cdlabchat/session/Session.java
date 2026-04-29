package com.cdlab.cdlabchat.session;

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

import java.time.Instant;

@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "joiner_id", nullable = false)
    private Long joinerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    public Session(Long creatorId, Long joinerId) {
        this.creatorId = creatorId;
        this.joinerId = joinerId;
        this.status = SessionStatus.SUSPENDED;
    }

    public boolean isMember(Long userId) {
        return creatorId.equals(userId) || joinerId.equals(userId);
    }

    public boolean isEnded() {
        return status == SessionStatus.ENDED;
    }

    public void activate() {
        guardNotEnded();
        this.status = SessionStatus.ACTIVE;
    }

    public void suspend() {
        guardNotEnded();
        this.status = SessionStatus.SUSPENDED;
    }

    public void end() {
        if (isEnded()) {
            return; // idempotent — 이미 종료된 세션 재호출은 no-op
        }
        this.status = SessionStatus.ENDED;
        this.endedAt = Instant.now();
    }

    private void guardNotEnded() {
        if (isEnded()) {
            throw new IllegalStateException("session already ended: id=" + id);
        }
    }
}
