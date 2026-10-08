package com.example.borrowing_service.messaging;

import com.example.borrowing_service.config.RabbitMQConfig;
import com.example.borrowing_service.entity.Borrowing;
import com.example.borrowing_service.event.BookBorrowedEvent;
import com.example.borrowing_service.event.BookOverdueEvent;
import com.example.borrowing_service.event.BookReturnedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Reliability model: publish-after-save, no transactional outbox (per project
 * decision). Each publish happens right after the corresponding repository.save()
 * commits. In the rare case the process crashes between the DB commit and the
 * publish call, the event is lost and downstream services never hear about it -
 * this gap is accepted and documented in the README rather than solved with an
 * outbox table. At-least-once delivery + idempotent consumers (see each
 * service's processed_events table) is the reliability story for everything
 * that *does* get published.
 */
@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishBookBorrowed(Borrowing borrowing) {
        BookBorrowedEvent event = new BookBorrowedEvent(
                UUID.randomUUID().toString(),
                borrowing.getId(),
                borrowing.getBookId(),
                borrowing.getMemberId(),
                borrowing.getBorrowDate(),
                borrowing.getDueDate()
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_BOOK_BORROWED, event);
        log.info("Published BookBorrowed event for borrowingId={}", borrowing.getId());
    }

    public void publishBookReturned(Borrowing borrowing, boolean wasOverdue) {
        BookReturnedEvent event = new BookReturnedEvent(
                UUID.randomUUID().toString(),
                borrowing.getId(),
                borrowing.getBookId(),
                borrowing.getMemberId(),
                borrowing.getDueDate(),
                borrowing.getReturnDate(),
                wasOverdue
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_BOOK_RETURNED, event);
        log.info("Published BookReturned event for borrowingId={}", borrowing.getId());
    }

    public void publishBookOverdue(Borrowing borrowing) {
        BookOverdueEvent event = new BookOverdueEvent(
                UUID.randomUUID().toString(),
                borrowing.getId(),
                borrowing.getBookId(),
                borrowing.getMemberId(),
                borrowing.getDueDate()
        );
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_BOOK_OVERDUE, event);
        log.info("Published BookOverdue event for borrowingId={}", borrowing.getId());
    }
}
