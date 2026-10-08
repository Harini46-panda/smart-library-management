package com.example.catalog_service.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Idempotency ledger: one row per event ID this service has already handled.
 * RabbitMQ gives at-least-once delivery, so consumers must be able to
 * recognize and safely ignore a redelivered message. Checking (and inserting
 * into) this table inside the same transaction as the business-logic update
 * is what makes each listener idempotent.
 */
@Entity
@Table(name = "catalog_processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent() {
    }

    public ProcessedEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }
}
