package com.example.borrowing_service.exception;

public class BookUnavailableException extends RuntimeException {

    public BookUnavailableException(Long bookId) {
        super("Book with id " + bookId + " has no available copies");
    }
}
