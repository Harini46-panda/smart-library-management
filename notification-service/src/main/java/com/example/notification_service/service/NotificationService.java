package com.example.notification_service.service;

import com.example.notification_service.entity.Notification;
import com.example.notification_service.entity.NotificationType;
import com.example.notification_service.entity.ProcessedEvent;
import com.example.notification_service.repository.NotificationRepository;
import com.example.notification_service.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ProcessedEventRepository processedEventRepository;

    public NotificationService(NotificationRepository notificationRepository,
                                ProcessedEventRepository processedEventRepository) {
        this.notificationRepository = notificationRepository;
        this.processedEventRepository = processedEventRepository;
    }

    public List<Notification> getAll() {
        return notificationRepository.findAll();
    }

    public List<Notification> getByMember(Long memberId) {
        return notificationRepository.findByMemberId(memberId);
    }

    @Transactional
    public void handleBookBorrowed(String eventId, Long memberId, Long bookId, LocalDate dueDate) {
        if (alreadyProcessed(eventId)) return;
        create(memberId, NotificationType.BOOK_BORROWED,
                "You have borrowed book #" + bookId + ". Please return it by " + dueDate + ".");
        markProcessed(eventId, "BookBorrowed");
    }

    @Transactional
    public void handleBookReturned(String eventId, Long memberId, Long bookId) {
        if (alreadyProcessed(eventId)) return;
        create(memberId, NotificationType.BOOK_RETURNED,
                "You have returned book #" + bookId + ". Thank you!");
        markProcessed(eventId, "BookReturned");
    }

    @Transactional
    public void handleFineGenerated(String eventId, Long memberId, Long borrowingId, BigDecimal amount) {
        if (alreadyProcessed(eventId)) return;
        create(memberId, NotificationType.FINE_GENERATED,
                "A fine of $" + amount + " has been generated for borrowing #" + borrowingId + ".");
        markProcessed(eventId, "FineGenerated");
    }

    @Transactional
    public void handleFinePaid(String eventId, Long memberId, BigDecimal amount) {
        if (alreadyProcessed(eventId)) return;
        create(memberId, NotificationType.FINE_PAID,
                "Your fine of $" + amount + " has been marked as paid. Thank you!");
        markProcessed(eventId, "FinePaid");
    }

    /**
     * There is no hold/reservation system in this project, so a book becoming
     * available again has no single addressed member - this is stored as a
     * general/broadcast notification (memberId left null).
     */
    @Transactional
    public void handleBookAvailable(String eventId, Long bookId, String bookTitle) {
        if (alreadyProcessed(eventId)) return;
        create(null, NotificationType.BOOK_AVAILABLE,
                "Book \"" + bookTitle + "\" (#" + bookId + ") is now available.");
        markProcessed(eventId, "BookAvailable");
    }

    private boolean alreadyProcessed(String eventId) {
        if (processedEventRepository.existsById(eventId)) {
            log.info("eventId={} already processed, skipping", eventId);
            return true;
        }
        return false;
    }

    private void create(Long memberId, NotificationType type, String message) {
        Notification notification = new Notification();
        notification.setMemberId(memberId);
        notification.setType(type);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }

    private void markProcessed(String eventId, String eventType) {
        processedEventRepository.save(new ProcessedEvent(eventId, eventType));
    }
}
