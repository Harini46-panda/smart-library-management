package com.example.borrowing_service.service;

import com.example.borrowing_service.client.BookView;
import com.example.borrowing_service.client.CatalogServiceClient;
import com.example.borrowing_service.client.MemberServiceClient;
import com.example.borrowing_service.dto.BorrowRequest;
import com.example.borrowing_service.entity.Borrowing;
import com.example.borrowing_service.entity.BorrowingStatus;
import com.example.borrowing_service.exception.BookUnavailableException;
import com.example.borrowing_service.exception.BorrowingNotFoundException;
import com.example.borrowing_service.exception.DuplicateReturnException;
import com.example.borrowing_service.messaging.EventPublisher;
import com.example.borrowing_service.repository.BorrowingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class BorrowingService {

    private final BorrowingRepository borrowingRepository;
    private final MemberServiceClient memberServiceClient;
    private final CatalogServiceClient catalogServiceClient;
    private final EventPublisher eventPublisher;
    private final int loanPeriodDays;

    public BorrowingService(BorrowingRepository borrowingRepository,
                             MemberServiceClient memberServiceClient,
                             CatalogServiceClient catalogServiceClient,
                             EventPublisher eventPublisher,
                             @Value("${library.borrowing.loan-period-days:14}") int loanPeriodDays) {
        this.borrowingRepository = borrowingRepository;
        this.memberServiceClient = memberServiceClient;
        this.catalogServiceClient = catalogServiceClient;
        this.eventPublisher = eventPublisher;
        this.loanPeriodDays = loanPeriodDays;
    }

    /**
     * Synchronous validation (member exists, book exists and has an available
     * copy) happens before anything is written, so a failed validation never
     * makes the borrow operation look like it succeeded. Only after the
     * Borrowing row is committed does the BookBorrowed event get published.
     */
    @Transactional
    public Borrowing borrow(BorrowRequest request) {
        memberServiceClient.getMemberOrThrow(request.getMemberId());

        BookView book = catalogServiceClient.getBookOrThrow(request.getBookId());
        if (book.getAvailableCopies() == null || book.getAvailableCopies() <= 0) {
            throw new BookUnavailableException(request.getBookId());
        }

        LocalDate today = LocalDate.now();
        Borrowing borrowing = new Borrowing();
        borrowing.setMemberId(request.getMemberId());
        borrowing.setBookId(request.getBookId());
        borrowing.setBorrowDate(today);
        borrowing.setDueDate(today.plusDays(loanPeriodDays));
        borrowing.setStatus(BorrowingStatus.BORROWED);

        Borrowing saved = borrowingRepository.save(borrowing);
        eventPublisher.publishBookBorrowed(saved);
        return saved;
    }

    public List<Borrowing> getAll() {
        return borrowingRepository.findAll();
    }

    public Borrowing getById(Long id) {
        return borrowingRepository.findById(id)
                .orElseThrow(() -> new BorrowingNotFoundException(id));
    }

    @Transactional
    public Borrowing returnBook(Long id) {
        Borrowing borrowing = borrowingRepository.findById(id)
                .orElseThrow(() -> new BorrowingNotFoundException(id));

        if (borrowing.getStatus() == BorrowingStatus.RETURNED) {
            throw new DuplicateReturnException(id);
        }

        LocalDate today = LocalDate.now();
        boolean wasOverdue = today.isAfter(borrowing.getDueDate());

        borrowing.setReturnDate(today);
        borrowing.setStatus(BorrowingStatus.RETURNED);

        Borrowing saved = borrowingRepository.save(borrowing);
        eventPublisher.publishBookReturned(saved, wasOverdue);
        return saved;
    }
}
