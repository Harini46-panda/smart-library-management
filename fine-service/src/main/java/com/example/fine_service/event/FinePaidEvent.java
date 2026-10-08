package com.example.fine_service.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Published by Fine Service on routing key "fine.paid". Consumed by Notification Service. */
public class FinePaidEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long fineId;
    private Long memberId;
    private BigDecimal amount;

    public FinePaidEvent() {
    }

    public FinePaidEvent(String eventId, Long fineId, Long memberId, BigDecimal amount) {
        this.eventId = eventId;
        this.eventType = "FinePaid";
        this.occurredAt = LocalDateTime.now();
        this.fineId = fineId;
        this.memberId = memberId;
        this.amount = amount;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getFineId() { return fineId; }
    public void setFineId(Long fineId) { this.fineId = fineId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
