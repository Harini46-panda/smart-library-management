package com.example.borrowing_service.exception;

public class DuplicateReturnException extends RuntimeException {

    public DuplicateReturnException(Long borrowingId) {
        super("Borrowing with id " + borrowingId + " has already been returned");
    }
}
