package com.example.catalog_service.service;

import com.example.catalog_service.dto.BookRequest;
import com.example.catalog_service.entity.Book;
import com.example.catalog_service.entity.ProcessedEvent;
import com.example.catalog_service.exception.BookNotFoundException;
import com.example.catalog_service.exception.DuplicateIsbnException;
import com.example.catalog_service.messaging.EventPublisher;
import com.example.catalog_service.repository.BookRepository;
import com.example.catalog_service.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BookService {

    private static final Logger log = LoggerFactory.getLogger(BookService.class);

    private final BookRepository bookRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final EventPublisher eventPublisher;

    public BookService(BookRepository bookRepository,
                        ProcessedEventRepository processedEventRepository,
                        EventPublisher eventPublisher) {
        this.bookRepository = bookRepository;
        this.processedEventRepository = processedEventRepository;
        this.eventPublisher = eventPublisher;
    }

    public Book create(BookRequest request) {
        if (bookRepository.findByIsbn(request.getIsbn()).isPresent()) {
            throw new DuplicateIsbnException(request.getIsbn());
        }

        Book book = new Book();
        book.setTitle(request.getTitle());
        book.setAuthor(request.getAuthor());
        book.setIsbn(request.getIsbn());
        book.setTotalCopies(request.getTotalCopies());
        book.setAvailableCopies(request.getTotalCopies());

        return bookRepository.save(book);
    }

    public List<Book> getAll() {
        return bookRepository.findAll();
    }

    public Book getById(Long id) {
        return bookRepository.findById(id)
                .orElseThrow(() -> new BookNotFoundException(id));
    }

    /**
     * Consumer-side handler for the BookBorrowed event. Idempotent: a
     * redelivered message with the same eventId is a silent no-op.
     */
    @Transactional
    public void handleBookBorrowed(String eventId, Long bookId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("BookBorrowed eventId={} already processed, skipping", eventId);
            return;
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        if (book.getAvailableCopies() > 0) {
            book.setAvailableCopies(book.getAvailableCopies() - 1);
            bookRepository.save(book);
        } else {
            // Should not normally happen: Borrowing Service checks availability
            // before creating the borrowing record. Logged as a safety net for
            // the race-condition window between that check and this event.
            log.warn("BookBorrowed event for bookId={} received but availableCopies already 0", bookId);
        }

        processedEventRepository.save(new ProcessedEvent(eventId, "BookBorrowed"));
    }

    /**
     * Consumer-side handler for the BookReturned event. Idempotent, and
     * publishes BookAvailable if the copy count just went from 0 to 1.
     */
    @Transactional
    public void handleBookReturned(String eventId, Long bookId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("BookReturned eventId={} already processed, skipping", eventId);
            return;
        }

        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BookNotFoundException(bookId));

        boolean wasFullyCheckedOut = book.getAvailableCopies() <= 0;

        if (book.getAvailableCopies() < book.getTotalCopies()) {
            book.setAvailableCopies(book.getAvailableCopies() + 1);
            bookRepository.save(book);
        } else {
            log.warn("BookReturned event for bookId={} received but availableCopies already at totalCopies", bookId);
        }

        processedEventRepository.save(new ProcessedEvent(eventId, "BookReturned"));

        if (wasFullyCheckedOut && book.getAvailableCopies() > 0) {
            eventPublisher.publishBookAvailable(book.getId(), book.getTitle());
        }
    }
}
