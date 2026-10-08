package com.example.catalog_service.event;

import java.time.LocalDateTime;

/**
 * Published by Catalog Service on routing key "book.available" when a book's
 * available copy count goes from 0 to >0 (i.e. it just became borrowable
 * again). Consumed by Notification Service. This project has no hold/reservation
 * system, so the resulting notification is a general announcement rather than
 * being addressed to a specific member.
 */
public class BookAvailableEvent {

    private String eventId;
    private String eventType;
    private LocalDateTime occurredAt;
    private Long bookId;
    private String bookTitle;

    public BookAvailableEvent() {
    }

    public BookAvailableEvent(String eventId, Long bookId, String bookTitle) {
        this.eventId = eventId;
        this.eventType = "BookAvailable";
        this.occurredAt = LocalDateTime.now();
        this.bookId = bookId;
        this.bookTitle = bookTitle;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }
}
