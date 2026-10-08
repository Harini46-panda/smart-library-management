package com.example.authentication_service.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final Map<String, String> users = Map.of(
            "admin", "admin123",
            "librarian", "lib123",
            "member", "member123"
    );

    private final Map<String, String> roles = Map.of(
            "admin", "ADMIN",
            "librarian", "LIBRARIAN",
            "member", "MEMBER"
    );

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        String storedPassword = users.get(request.username());
        if (storedPassword == null || !storedPassword.equals(request.password())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        }

        String role = roles.get(request.username());
        String token = "token_" + request.username() + "_" + UUID.randomUUID();

        return ResponseEntity.ok(new AuthResponse(token, request.username(), role, "Login successful"));
    }

    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validate(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization == null || authorization.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing authorization header");
        }

        String token = authorization.replace("Bearer ", "").trim();
        if (token.isBlank() || !token.startsWith("token_")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }

        return ResponseEntity.ok(Map.of(
                "valid", true,
                "token", token,
                "message", "Token accepted"
        ));
    }

    public record LoginRequest(String username, String password) {}

    public record AuthResponse(String token, String username, String role, String message) {}
}
