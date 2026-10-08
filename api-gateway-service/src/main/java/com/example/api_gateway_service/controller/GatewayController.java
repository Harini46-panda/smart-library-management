package com.example.api_gateway_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/gateway")
public class GatewayController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "api-gateway-service",
                "message", "Gateway is running"
        ));
    }

    @GetMapping("/services")
    public ResponseEntity<List<Map<String, String>>> services() {
        return ResponseEntity.ok(List.of(
                Map.of("name", "member-service", "url", "http://localhost:8081"),
                Map.of("name", "catalog-service", "url", "http://localhost:8082"),
                Map.of("name", "borrowing-service", "url", "http://localhost:8083"),
                Map.of("name", "fine-service", "url", "http://localhost:8084"),
                Map.of("name", "notification-service", "url", "http://localhost:8085"),
                Map.of("name", "authentication-service", "url", "http://localhost:8086"),
                Map.of("name", "audit-service", "url", "http://localhost:8088"),
                Map.of("name", "recommendation-service", "url", "http://localhost:8089")
        ));
    }

    @GetMapping("/route/{serviceName}")
    public ResponseEntity<Map<String, String>> route(@PathVariable String serviceName) {
        return ResponseEntity.ok(Map.of(
                "service", serviceName,
                "status", "forwarded",
                "message", "Request routed through API gateway"
        ));
    }
}
