package com.example.fine_service.controller;

import com.example.fine_service.entity.Fine;
import com.example.fine_service.service.FineService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin
public class FineController {

    private final FineService service;

    public FineController(FineService service) {
        this.service = service;
    }

    @GetMapping("/members/{id}/fines")
    public List<Fine> getByMember(@PathVariable("id") Long memberId) {
        return service.getByMember(memberId);
    }

    @GetMapping("/fines")
    public List<Fine> getAll() {
        return service.getAll();
    }

    @PutMapping("/fines/{id}/pay")
    public Fine pay(@PathVariable Long id) {
        return service.pay(id);
    }
}
