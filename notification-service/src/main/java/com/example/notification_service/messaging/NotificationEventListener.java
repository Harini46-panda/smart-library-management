package com.example.notification_service.messaging;

import com.example.notification_service.config.RabbitMQConfig;
import com.example.notification_service.event.*;
import com.example.notification_service.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Every listener here uses containerFactory = "retryContainerFactory" so a
 * thrown exception goes through bounded retry and then dead-lettering (see
 * RabbitMQConfig for the full flow), rather than the default
 * infinite-requeue behavior.
 */
@Component
public class NotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventListener.class);

    private final NotificationService notificationService;

    public NotificationEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOOK_BORROWED, containerFactory = "retryContainerFactory")
    public void onBookBorrowed(BookBorrowedEvent event) {
        log.info("Received BookBorrowed event: eventId={}", event.getEventId());
        notificationService.handleBookBorrowed(event.getEventId(), event.getMemberId(), event.getBookId(), event.getDueDate());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOOK_RETURNED, containerFactory = "retryContainerFactory")
    public void onBookReturned(BookReturnedEvent event) {
        log.info("Received BookReturned event: eventId={}", event.getEventId());
        notificationService.handleBookReturned(event.getEventId(), event.getMemberId(), event.getBookId());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_FINE_GENERATED, containerFactory = "retryContainerFactory")
    public void onFineGenerated(FineGeneratedEvent event) {
        log.info("Received FineGenerated event: eventId={}", event.getEventId());
        notificationService.handleFineGenerated(event.getEventId(), event.getMemberId(), event.getBorrowingId(), event.getAmount());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_FINE_PAID, containerFactory = "retryContainerFactory")
    public void onFinePaid(FinePaidEvent event) {
        log.info("Received FinePaid event: eventId={}", event.getEventId());
        notificationService.handleFinePaid(event.getEventId(), event.getMemberId(), event.getAmount());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOOK_AVAILABLE, containerFactory = "retryContainerFactory")
    public void onBookAvailable(BookAvailableEvent event) {
        log.info("Received BookAvailable event: eventId={}", event.getEventId());
        notificationService.handleBookAvailable(event.getEventId(), event.getBookId(), event.getBookTitle());
    }
}
