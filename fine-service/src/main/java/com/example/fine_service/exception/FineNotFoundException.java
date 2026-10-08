package com.example.fine_service.exception;

public class FineNotFoundException extends RuntimeException {

    public FineNotFoundException(Long id) {
        super("Fine not found with id: " + id);
    }
}
