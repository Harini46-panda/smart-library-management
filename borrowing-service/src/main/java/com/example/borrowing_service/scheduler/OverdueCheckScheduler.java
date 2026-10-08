package com.example.borrowing_service.scheduler;

import com.example.borrowing_service.entity.Borrowing;
import com.example.borrowing_service.entity.BorrowingStatus;
import com.example.borrowing_service.repository.BorrowingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Runs on the configured cron schedule (default: every hour, on the hour) and
 * publishes a BookOverdue event for every BORROWED borrowing whose due date
 * has passed and which has not already had an overdue event published for it.
 *
 * Duplicate-prevention: the overdueEventPublished flag is persisted per
 * borrowing (by OverdueEventProcessor, in the same transaction as the event
 * publish), so:
 *   - the same borrowing is never re-published on the next scheduled run
 *   - a restart does not cause a burst of re-published events, since the
 *     flag survives in the database
 *
 * One borrowing's failure (e.g. a transient RabbitMQ blip) is caught and
 * logged so it does not abort the whole batch; that borrowing will simply be
 * picked up again on the next run since its flag was never set.
 */
@Component
public class OverdueCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(OverdueCheckScheduler.class);

    private final BorrowingRepository borrowingRepository;
    private final OverdueEventProcessor overdueEventProcessor;

    public OverdueCheckScheduler(BorrowingRepository borrowingRepository, OverdueEventProcessor overdueEventProcessor) {
        this.borrowingRepository = borrowingRepository;
        this.overdueEventProcessor = overdueEventProcessor;
    }

    @Scheduled(cron = "${library.overdue-check.cron:0 0 * * * *}")
    public void checkOverdueBorrowings() {
        List<Borrowing> overdue = borrowingRepository
                .findByStatusAndDueDateBeforeAndOverdueEventPublishedFalse(BorrowingStatus.BORROWED, LocalDate.now());

        if (overdue.isEmpty()) {
            return;
        }

        log.info("Overdue check found {} newly-overdue borrowing(s)", overdue.size());

        for (Borrowing borrowing : overdue) {
            try {
                overdueEventProcessor.markOverdueAndPublish(borrowing.getId());
            } catch (Exception ex) {
                log.error("Failed to process overdue borrowingId={}, will retry on next run", borrowing.getId(), ex);
            }
        }
    }
}
