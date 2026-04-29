package com.cdlab.cdlabchat.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByClientEventId(UUID clientEventId);
}
