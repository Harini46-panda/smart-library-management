package com.example.catalog_service.messaging;

import com.example.catalog_service.config.RabbitMQConfig;
import com.example.catalog_service.event.BookAvailableEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Fired only when a book's available copies goes from 0 to >0. Reliability
     * model is deliberately simple (matches the Borrowing Service design):
     * the DB update is committed first, then this publish happens; in the rare
     * case the process crashes in between, the event is lost. That gap is
     * accepted and documented rather than solved with a transactional outbox.
     */
    public void publishBookAvailable(Long bookId, String bookTitle) {
        BookAvailableEvent event = new BookAvailableEvent(UUID.randomUUID().toString(), bookId, bookTitle);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_BOOK_AVAILABLE, event);
        log.info("Published BookAvailable event for bookId={}", bookId);
    }
}
