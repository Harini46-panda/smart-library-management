package com.example.catalog_service.config;

import com.example.catalog_service.event.BookAvailableEvent;
import com.example.catalog_service.event.BookBorrowedEvent;
import com.example.catalog_service.event.BookReturnedEvent;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared library-wide topic exchange "library.events". Every service that
 * publishes or consumes domain events declares (idempotently, via
 * durable + auto-declare) the same exchange name and routing keys so the
 * topology is consistent no matter which service starts first.
 *
 * Routing keys used across the system:
 *   book.borrowed | book.returned | book.overdue | fine.generated | fine.paid | book.available
 *
 * Catalog Service is a competing consumer on its own queues: run more than
 * one instance of catalog-service and RabbitMQ will load-balance deliveries
 * across them (classic work-queue behavior) while Borrowing/Fine/Notification
 * each get their own independent copy of the same event (publish/subscribe).
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "library.events";

    public static final String ROUTING_BOOK_BORROWED = "book.borrowed";
    public static final String ROUTING_BOOK_RETURNED = "book.returned";
    public static final String ROUTING_BOOK_AVAILABLE = "book.available";

    public static final String QUEUE_BOOK_BORROWED = "catalog.book-borrowed.queue";
    public static final String QUEUE_BOOK_RETURNED = "catalog.book-returned.queue";

    @Bean
    public TopicExchange libraryEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue bookBorrowedQueue() {
        return QueueBuilder.durable(QUEUE_BOOK_BORROWED).build();
    }

    @Bean
    public Queue bookReturnedQueue() {
        return QueueBuilder.durable(QUEUE_BOOK_RETURNED).build();
    }

    @Bean
    public Binding bookBorrowedBinding(Queue bookBorrowedQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(bookBorrowedQueue).to(libraryEventsExchange).with(ROUTING_BOOK_BORROWED);
    }

    @Bean
    public Binding bookReturnedBinding(Queue bookReturnedQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(bookReturnedQueue).to(libraryEventsExchange).with(ROUTING_BOOK_RETURNED);
    }

    /**
     * Reuses Spring Boot's auto-configured ObjectMapper (JavaTimeModule + ISO-8601
     * date formatting already applied) instead of a bare JacksonJsonMessageConverter,
     * so LocalDate/LocalDateTime fields on event payloads serialize/deserialize
     * identically to how the REST layer handles them.
     *
     * Type mapping: there is no shared DTO module between services (each service
     * is independently deployable), so every service keeps its own local copy of
     * each event class in its own package. The default Jackson2JsonMessageConverter
     * would stamp the __TypeId__ header with the *publisher's* fully-qualified
     * class name (e.g. com.example.borrowing_service.event.BookBorrowedEvent),
     * which does not exist on this service's classpath and would blow up on
     * deserialization. Instead we map short, stable string ids <-> local classes
     * on both the publishing and consuming sides, so as long as every service
     * uses the same string id for "the BookBorrowed event", cross-service JSON
     * (de)serialization works without any shared library.
     */
    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper objectMapper) {
        JacksonJsonMessageConverter converter =
                new JacksonJsonMessageConverter(objectMapper);

        DefaultJacksonJavaTypeMapper typeMapper =
                new DefaultJacksonJavaTypeMapper();

        Map<String, Class<?>> typeMapping = new HashMap<>();
        typeMapping.put("bookBorrowedEvent", BookBorrowedEvent.class);
        typeMapping.put("bookReturnedEvent", BookReturnedEvent.class);
        typeMapping.put("bookAvailableEvent", BookAvailableEvent.class);

        typeMapper.setIdClassMapping(typeMapping);
        typeMapper.setTrustedPackages("*");
        converter.setJavaTypeMapper(typeMapper);

        return converter;
    }
}
