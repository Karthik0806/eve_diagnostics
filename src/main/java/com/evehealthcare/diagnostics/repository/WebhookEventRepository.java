package com.evehealthcare.diagnostics.repository;

import com.evehealthcare.diagnostics.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {
    Optional<WebhookEvent> findByEventId(String eventId);
    boolean existsByEventId(String eventId);
}
