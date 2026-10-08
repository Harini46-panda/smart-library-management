package com.example.audit_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final List<Map<String, Object>> auditEntries = new ArrayList<>();

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> recordEvent(@RequestBody AuditEventRequest request) {
        Map<String, Object> entry = Map.of(
                "id", auditEntries.size() + 1,
                "actor", request.actor(),
                "action", request.action(),
                "resource", request.resource(),
                "details", request.details(),
                "timestamp", Instant.now().toString()
        );
        auditEntries.add(entry);

        return ResponseEntity.ok(Map.of(
                "status", "recorded",
                "entry", entry
        ));
    }

    @GetMapping("/events")
    public ResponseEntity<List<Map<String, Object>>> listEvents() {
        return ResponseEntity.ok(auditEntries);
    }

    public record AuditEventRequest(String actor, String action, String resource, String details) {}
}
