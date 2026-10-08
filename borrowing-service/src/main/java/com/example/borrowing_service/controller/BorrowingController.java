package com.example.borrowing_service.controller;

import com.example.borrowing_service.dto.BorrowRequest;
import com.example.borrowing_service.entity.Borrowing;
import com.example.borrowing_service.service.BorrowingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/borrowings")
@CrossOrigin
public class BorrowingController {

    private final BorrowingService service;

    public BorrowingController(BorrowingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Borrowing borrow(@Valid @RequestBody BorrowRequest request) {
        return service.borrow(request);
    }

    @GetMapping
    public List<Borrowing> getAll() {
        return service.getAll();
    }

    @GetMapping("/{id}")
    public Borrowing getById(@PathVariable Long id) {
        return service.getById(id);
    }

    @PutMapping("/{id}/return")
    public Borrowing returnBook(@PathVariable Long id) {
        return service.returnBook(id);
    }
}
