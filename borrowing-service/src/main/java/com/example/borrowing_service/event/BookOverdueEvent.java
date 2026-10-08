package com.example.borrowing_service.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class BookOverdueEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long borrowingId;
    private Long bookId;
    private Long memberId;
    private LocalDate dueDate;

    public BookOverdueEvent() {
    }

    public BookOverdueEvent(String eventId, Long borrowingId, Long bookId, Long memberId, LocalDate dueDate) {
        this.eventId = eventId;
        this.eventType = "BookOverdue";
        this.occurredAt = LocalDateTime.now();
        this.borrowingId = borrowingId;
        this.bookId = bookId;
        this.memberId = memberId;
        this.dueDate = dueDate;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(LocalDateTime occurredAt) { this.occurredAt = occurredAt; }
    public Long getBorrowingId() { return borrowingId; }
    public void setBorrowingId(Long borrowingId) { this.borrowingId = borrowingId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
}
