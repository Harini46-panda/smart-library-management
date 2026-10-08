package com.example.fine_service.messaging;

import com.example.fine_service.config.RabbitMQConfig;
import com.example.fine_service.event.BookOverdueEvent;
import com.example.fine_service.event.BookReturnedEvent;
import com.example.fine_service.service.FineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FineEventListener {

    private static final Logger log = LoggerFactory.getLogger(FineEventListener.class);

    private final FineService fineService;

    public FineEventListener(FineService fineService) {
        this.fineService = fineService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOOK_OVERDUE)
    public void onBookOverdue(BookOverdueEvent event) {
        log.info("Received BookOverdue event: eventId={}, borrowingId={}", event.getEventId(), event.getBorrowingId());
        fineService.handleBookOverdue(event.getEventId(), event.getBorrowingId(), event.getMemberId(), event.getDueDate());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOOK_RETURNED)
    public void onBookReturned(BookReturnedEvent event) {
        log.info("Received BookReturned event: eventId={}, borrowingId={}", event.getEventId(), event.getBorrowingId());
        fineService.handleBookReturned(event.getEventId(), event.getBorrowingId(), event.getMemberId(),
                event.getDueDate(), event.getReturnDate(), event.isWasOverdue());
    }
}
