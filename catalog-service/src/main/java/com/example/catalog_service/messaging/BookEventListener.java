package com.example.catalog_service.messaging;

import com.example.catalog_service.config.RabbitMQConfig;
import com.example.catalog_service.event.BookBorrowedEvent;
import com.example.catalog_service.event.BookReturnedEvent;
import com.example.catalog_service.service.BookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BookEventListener {

    private static final Logger log = LoggerFactory.getLogger(BookEventListener.class);

    private final BookService bookService;

    public BookEventListener(BookService bookService) {
        this.bookService = bookService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOOK_BORROWED)
    public void onBookBorrowed(BookBorrowedEvent event) {
        log.info("Received BookBorrowed event: eventId={}, bookId={}", event.getEventId(), event.getBookId());
        bookService.handleBookBorrowed(event.getEventId(), event.getBookId());
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_BOOK_RETURNED)
    public void onBookReturned(BookReturnedEvent event) {
        log.info("Received BookReturned event: eventId={}, bookId={}", event.getEventId(), event.getBookId());
        bookService.handleBookReturned(event.getEventId(), event.getBookId());
    }
}
