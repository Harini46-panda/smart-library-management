package com.example.recommendation_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/recommendations")
public class RecommendationController {

    private static final String THE_HOBBIT = "The Hobbit";
    private static final String CLEAN_CODE = "Clean Code";
    private static final String ATOMIC_HABITS = "Atomic Habits";
    private static final String TITLE = "title";
    private static final String SCORE = "score";

    private final Map<Integer, List<String>> recommendationsByMember = Map.of(
            1, List.of(THE_HOBBIT, CLEAN_CODE, ATOMIC_HABITS),
            2, List.of("Java Concurrency in Practice", "Design Patterns", "The Pragmatic Programmer"),
            3, List.of("Deep Work", "The Alchemist", "Software Architecture"),
            4, List.of("Spring in Action", "Effective Java", "Domain-Driven Design")
    );

    @GetMapping("/member/{memberId}")
    public ResponseEntity<List<String>> getForMember(@PathVariable int memberId) {
        List<String> recommendations = recommendationsByMember.getOrDefault(memberId, List.of(
                THE_HOBBIT,
                CLEAN_CODE,
                "The Pragmatic Programmer"
        ));

        return ResponseEntity.ok(recommendations);
    }

    @GetMapping("/popular")
    public ResponseEntity<List<Map<String, Object>>> getPopular() {
        return ResponseEntity.ok(List.of(
                Map.of(TITLE, THE_HOBBIT, SCORE, 98),
                Map.of(TITLE, CLEAN_CODE, SCORE, 94),
                Map.of(TITLE, ATOMIC_HABITS, SCORE, 91)
        ));
    }
}
