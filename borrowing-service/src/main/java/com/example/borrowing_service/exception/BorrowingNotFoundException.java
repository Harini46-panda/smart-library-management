package com.example.borrowing_service.exception;

public class BorrowingNotFoundException extends RuntimeException {

    public BorrowingNotFoundException(Long id) {
        super("Borrowing not found with id: " + id);
    }
}
