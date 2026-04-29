package com.cdlab.cdlabchat.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * events 의 projection cache. 권위는 events.
 * UPDATE-on-edit 정책 — content 는 항상 최신 상태.
 * soft delete — deleted_at != NULL.
 */
@Entity
@Table(name = "messages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Message(Long sessionId, Long userId, String content) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.content = content;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isEdited() {
        return editedAt != null;
    }

    public void edit(String newContent) {
        if (isDeleted()) {
            throw new IllegalStateException("cannot edit deleted message: id=" + id);
        }
        this.content = newContent;
        this.editedAt = Instant.now();
    }

    public void softDelete() {
        if (isDeleted()) {
            return; // idempotent
        }
        this.deletedAt = Instant.now();
    }
}
