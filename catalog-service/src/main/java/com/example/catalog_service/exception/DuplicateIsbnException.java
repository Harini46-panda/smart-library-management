package com.example.catalog_service.exception;

public class DuplicateIsbnException extends RuntimeException {

    public DuplicateIsbnException(String isbn) {
        super("Book already exists with ISBN: " + isbn);
    }
}
