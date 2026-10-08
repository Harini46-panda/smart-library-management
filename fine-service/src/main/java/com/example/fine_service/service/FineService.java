package com.example.fine_service.service;

import com.example.fine_service.entity.Fine;
import com.example.fine_service.entity.FineStatus;
import com.example.fine_service.entity.ProcessedEvent;
import com.example.fine_service.exception.FineAlreadyPaidException;
import com.example.fine_service.exception.FineNotFoundException;
import com.example.fine_service.messaging.EventPublisher;
import com.example.fine_service.repository.FineRepository;
import com.example.fine_service.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class FineService {

    private static final Logger log = LoggerFactory.getLogger(FineService.class);

    private final FineRepository fineRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EventPublisher eventPublisher;
    private final BigDecimal finePerDay;

    public FineService(FineRepository fineRepository,
                        ProcessedEventRepository processedEventRepository,
                        EventPublisher eventPublisher,
                        @Value("${library.fine.per-day-amount:10.00}") BigDecimal finePerDay) {
        this.fineRepository = fineRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
        this.finePerDay = finePerDay;
    }

    public List<Fine> getByMember(Long memberId) {
        return fineRepository.findByMemberId(memberId);
    }

    public List<Fine> getAll() {
        return fineRepository.findAll();
    }

    @Transactional
    public Fine pay(Long id) {
        Fine fine = fineRepository.findById(id)
                .orElseThrow(() -> new FineNotFoundException(id));

        if (fine.getStatus() == FineStatus.PAID) {
            throw new FineAlreadyPaidException(id);
        }

        fine.setStatus(FineStatus.PAID);
        Fine saved = fineRepository.save(fine);
        eventPublisher.publishFinePaid(saved);
        return saved;
    }

    /**
     * BookOverdue handler: the borrowing is still out and overdue. Creates an
     * "accruing" fine the first time this is seen for a borrowing; a unique
     * DB constraint on borrowing_id plus this existence check means a
     * redelivered/duplicate event is a safe no-op.
     */
    @Transactional
    public void handleBookOverdue(String eventId, Long borrowingId, Long memberId, LocalDate dueDate) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("BookOverdue eventId={} already processed, skipping", eventId);
            return;
        }

        Optional<Fine> existing = fineRepository.findByBorrowingId(borrowingId);
        if (existing.isEmpty()) {
            long daysLate = Math.max(1, ChronoUnit.DAYS.between(dueDate, LocalDate.now()));
            Fine fine = new Fine();
            fine.setBorrowingId(borrowingId);
            fine.setMemberId(memberId);
            fine.setDueDate(dueDate);
            fine.setAmount(finePerDay.multiply(BigDecimal.valueOf(daysLate)));
            fine.setStatus(FineStatus.UNPAID);

            Fine saved = fineRepository.save(fine);
            processedEventRepository.save(new ProcessedEvent(eventId, "BookOverdue"));
            eventPublisher.publishFineGenerated(saved);
        } else {
            log.info("Fine already exists for borrowingId={}, BookOverdue event is a no-op", borrowingId);
            processedEventRepository.save(new ProcessedEvent(eventId, "BookOverdue"));
        }
    }

    /**
     * BookReturned handler: only acts when the return was late (wasOverdue).
     * If an accruing fine already exists (created earlier by a BookOverdue
     * event), its amount is recalculated against the actual return date. If
     * none exists yet (book was returned late enough to be overdue, but the
     * hourly overdue-check scheduler never got a chance to fire first), a new
     * fine is created here. Either way, exactly one Fine row per borrowing.
     */
    @Transactional
    public void handleBookReturned(String eventId, Long borrowingId, Long memberId,
                                    LocalDate dueDate, LocalDate returnDate, boolean wasOverdue) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("BookReturned eventId={} already processed, skipping", eventId);
            return;
        }

        if (!wasOverdue) {
            // Not late - no fine for a borrowing that isn't overdue.
            processedEventRepository.save(new ProcessedEvent(eventId, "BookReturned"));
            return;
        }

        long daysLate = Math.max(1, ChronoUnit.DAYS.between(dueDate, returnDate));
        BigDecimal finalAmount = finePerDay.multiply(BigDecimal.valueOf(daysLate));

        Optional<Fine> existing = fineRepository.findByBorrowingId(borrowingId);
        if (existing.isPresent()) {
            Fine fine = existing.get();
            if (fine.getStatus() != FineStatus.PAID) {
                fine.setAmount(finalAmount);
                fine.setReturnDate(returnDate);
                fineRepository.save(fine);
            }
        } else {
            Fine fine = new Fine();
            fine.setBorrowingId(borrowingId);
            fine.setMemberId(memberId);
            fine.setDueDate(dueDate);
            fine.setReturnDate(returnDate);
            fine.setAmount(finalAmount);
            fine.setStatus(FineStatus.UNPAID);
            Fine saved = fineRepository.save(fine);
            eventPublisher.publishFineGenerated(saved);
        }

        processedEventRepository.save(new ProcessedEvent(eventId, "BookReturned"));
    }
}
