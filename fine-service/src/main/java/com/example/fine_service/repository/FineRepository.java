package com.example.fine_service.repository;

import com.example.fine_service.entity.Fine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FineRepository extends JpaRepository<Fine, Long> {

    List<Fine> findByMemberId(Long memberId);

    Optional<Fine> findByBorrowingId(Long borrowingId);
}
