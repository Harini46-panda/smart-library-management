package com.example.borrowing_service.repository;

import com.example.borrowing_service.entity.Borrowing;
import com.example.borrowing_service.entity.BorrowingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BorrowingRepository extends JpaRepository<Borrowing, Long> {

    List<Borrowing> findByStatusAndDueDateBeforeAndOverdueEventPublishedFalse(
            BorrowingStatus status, LocalDate date);
}
