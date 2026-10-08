package com.example.borrowing_service.scheduler;

import com.example.borrowing_service.entity.Borrowing;
import com.example.borrowing_service.messaging.EventPublisher;
import com.example.borrowing_service.repository.BorrowingRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kept as a separate bean (rather than a private method on OverdueCheckScheduler)
 * so that @Transactional actually takes effect: Spring's proxy-based AOP does
 * not intercept a method called via "this." from within the same class, so
 * this logic has to live behind its own bean reference to get real
 * transaction boundaries per borrowing.
 */
@Component
public class OverdueEventProcessor {

    private final BorrowingRepository borrowingRepository;
    private final EventPublisher eventPublisher;

    public OverdueEventProcessor(BorrowingRepository borrowingRepository, EventPublisher eventPublisher) {
        this.borrowingRepository = borrowingRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void markOverdueAndPublish(Long borrowingId) {
        Borrowing borrowing = borrowingRepository.findById(borrowingId).orElseThrow();
        if (Boolean.TRUE.equals(borrowing.getOverdueEventPublished())) {
            return;
        }
        borrowing.setOverdueEventPublished(true);
        Borrowing saved = borrowingRepository.save(borrowing);
        eventPublisher.publishBookOverdue(saved);
    }
}
