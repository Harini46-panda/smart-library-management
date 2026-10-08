package com.example.fine_service.event;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Mirrors the payload published by Borrowing Service on routing key "book.returned". */
public class BookReturnedEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long borrowingId;
    private Long bookId;
    private Long memberId;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private boolean wasOverdue;

    public BookReturnedEvent() {
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
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public boolean isWasOverdue() { return wasOverdue; }
    public void setWasOverdue(boolean wasOverdue) { this.wasOverdue = wasOverdue; }
}
