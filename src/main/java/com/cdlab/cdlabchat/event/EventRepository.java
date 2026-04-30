package com.cdlab.cdlabchat.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByClientEventId(UUID clientEventId);

    // 스냅샷 fold 입력. idx_events_session_created (session_id, created_at) 가 그대로 커버.
    List<Event> findBySessionIdAndCreatedAtLessThanEqualOrderByCreatedAtAsc(Long sessionId, Instant at);
}
