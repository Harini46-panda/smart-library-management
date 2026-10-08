package com.example.member_service.exception;

public class DuplicateMemberException extends RuntimeException {

    public DuplicateMemberException(String email) {
        super("Member already exists with email: " + email);
    }
}