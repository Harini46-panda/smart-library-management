package com.example.borrowing_service.exception;

/**
 * Wraps a failure calling Member Service or Catalog Service (connection
 * refused, timeout, unexpected 5xx) so it surfaces to the caller as a
 * distinct, clearly-labeled error instead of a generic 500 with a stack
 * trace about RestClient internals.
 */
public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String message) {
        super(message);
    }
}
