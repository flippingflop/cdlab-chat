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

    @Column(name = "creator_left_at")
    private Instant creatorLeftAt;

    @Column(name = "joiner_left_at")
    private Instant joinerLeftAt;

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

    /**
     * 해당 사용자가 명시적으로 LEAVE 한 적이 있는가.
     * 비멤버에 대해서는 false.
     */
    public boolean isLeftBy(Long userId) {
        if (creatorId.equals(userId)) return creatorLeftAt != null;
        if (joinerId.equals(userId)) return joinerLeftAt != null;
        return false;
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

    /**
     * 호출자(=userId) 의 가시성 마커를 세팅하고, 세션을 ENDED 로 전이시킨다.
     * - 같은 사용자가 다시 호출하면 left_at 은 첫 시각 유지 (no-op)
     * - 이미 ENDED 인 세션이면 status 는 그대로 (end() 가 idempotent)
     * 비멤버 호출은 도메인 무결성 위반.
     */
    public void leave(Long userId) {
        if (creatorId.equals(userId)) {
            if (creatorLeftAt == null) {
                this.creatorLeftAt = Instant.now();
            }
        } else if (joinerId.equals(userId)) {
            if (joinerLeftAt == null) {
                this.joinerLeftAt = Instant.now();
            }
        } else {
            throw new IllegalStateException(
                    "non-member cannot leave session: sessionId=" + id + ", userId=" + userId);
        }
        end();
    }

    private void guardNotEnded() {
        if (isEnded()) {
            throw new IllegalStateException("session already ended: id=" + id);
        }
    }
}
