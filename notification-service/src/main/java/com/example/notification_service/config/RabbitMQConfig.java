package com.example.notification_service.config;

import com.example.notification_service.event.*;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.rabbit.config.StatelessRetryOperationsInterceptor;
import java.util.HashMap;
import java.util.Map;

/**
 * Notification Service is the one place in the system that implements the
 * full reliability pattern the project asked for: bounded retry, then a
 * Dead Letter Exchange/Queue per event type, with acknowledgement/rejection
 * behavior wired so a permanently-failing message ends up parked (and
 * inspectable) in RabbitMQ Management rather than looping forever or being
 * silently dropped.
 *
 * Flow for one message:
 *   1. Listener throws -> RetryOperationsInterceptor catches it, retries the
 *      listener method in-process up to library.notification.retry.max-attempts
 *      times (default 3), waiting library.notification.retry.backoff-ms between
 *      attempts (default 2000ms).
 *   2. If every attempt fails, the interceptor's recoverer
 *      (RejectAndDontRequeueRecoverer) rejects the message without requeueing it.
 *   3. Because each main queue declares x-dead-letter-exchange = library.dlx
 *      (and a per-queue x-dead-letter-routing-key), RabbitMQ automatically
 *      routes that rejected message to its matching *.dlq queue instead of
 *      discarding it.
 *   4. Nothing consumes the DLQs automatically - inspect them in RabbitMQ
 *      Management (http://localhost:15672 -> Queues) to see failed messages,
 *      their arguments, and the x-death header showing why/when they died.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "library.events";
    public static final String DLX = "library.dlx";

    public static final String ROUTING_BOOK_BORROWED = "book.borrowed";
    public static final String ROUTING_BOOK_RETURNED = "book.returned";
    public static final String ROUTING_FINE_GENERATED = "fine.generated";
    public static final String ROUTING_FINE_PAID = "fine.paid";
    public static final String ROUTING_BOOK_AVAILABLE = "book.available";

    public static final String QUEUE_BOOK_BORROWED = "notification.book-borrowed.queue";
    public static final String QUEUE_BOOK_RETURNED = "notification.book-returned.queue";
    public static final String QUEUE_FINE_GENERATED = "notification.fine-generated.queue";
    public static final String QUEUE_FINE_PAID = "notification.fine-paid.queue";
    public static final String QUEUE_BOOK_AVAILABLE = "notification.book-available.queue";

    private static final String DLQ_ROUTING_BOOK_BORROWED = "dlq.book-borrowed";
    private static final String DLQ_ROUTING_BOOK_RETURNED = "dlq.book-returned";
    private static final String DLQ_ROUTING_FINE_GENERATED = "dlq.fine-generated";
    private static final String DLQ_ROUTING_FINE_PAID = "dlq.fine-paid";
    private static final String DLQ_ROUTING_BOOK_AVAILABLE = "dlq.book-available";

    private static final String X_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    private static final String X_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    private static final Map<String, Class<?>> EVENT_TYPE_MAPPING = createEventTypeMapping();

    private static Map<String, Class<?>> createEventTypeMapping() {
        Map<String, Class<?>> mapping = new HashMap<>();
        mapping.put("bookBorrowedEvent", BookBorrowedEvent.class);
        mapping.put("bookReturnedEvent", BookReturnedEvent.class);
        mapping.put("fineGeneratedEvent", FineGeneratedEvent.class);
        mapping.put("finePaidEvent", FinePaidEvent.class);
        mapping.put("bookAvailableEvent", BookAvailableEvent.class);
        return mapping;
    }

    // ---- Main topic exchange + DLX ----

    @Bean
    public TopicExchange libraryEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public TopicExchange libraryDeadLetterExchange() {
        return ExchangeBuilder.topicExchange(DLX).durable(true).build();
    }

    // ---- Main queues (each wired to dead-letter into its own DLQ) ----

    @Bean
    public Queue bookBorrowedQueue() {
        return QueueBuilder.durable(QUEUE_BOOK_BORROWED)
                .withArgument(X_DEAD_LETTER_EXCHANGE, DLX)
                .withArgument(X_DEAD_LETTER_ROUTING_KEY, DLQ_ROUTING_BOOK_BORROWED)
                .build();
    }

    @Bean
    public Queue bookReturnedQueue() {
        return QueueBuilder.durable(QUEUE_BOOK_RETURNED)
                .withArgument(X_DEAD_LETTER_EXCHANGE, DLX)
                .withArgument(X_DEAD_LETTER_ROUTING_KEY, DLQ_ROUTING_BOOK_RETURNED)
                .build();
    }

    @Bean
    public Queue fineGeneratedQueue() {
        return QueueBuilder.durable(QUEUE_FINE_GENERATED)
                .withArgument(X_DEAD_LETTER_EXCHANGE, DLX)
                .withArgument(X_DEAD_LETTER_ROUTING_KEY, DLQ_ROUTING_FINE_GENERATED)
                .build();
    }

    @Bean
    public Queue finePaidQueue() {
        return QueueBuilder.durable(QUEUE_FINE_PAID)
                .withArgument(X_DEAD_LETTER_EXCHANGE, DLX)
                .withArgument(X_DEAD_LETTER_ROUTING_KEY, DLQ_ROUTING_FINE_PAID)
                .build();
    }

    @Bean
    public Queue bookAvailableQueue() {
        return QueueBuilder.durable(QUEUE_BOOK_AVAILABLE)
                .withArgument(X_DEAD_LETTER_EXCHANGE, DLX)
                .withArgument(X_DEAD_LETTER_ROUTING_KEY, DLQ_ROUTING_BOOK_AVAILABLE)
                .build();
    }

    // ---- Main queue bindings ----

    @Bean
    public Binding bookBorrowedBinding(Queue bookBorrowedQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(bookBorrowedQueue).to(libraryEventsExchange).with(ROUTING_BOOK_BORROWED);
    }

    @Bean
    public Binding bookReturnedBinding(Queue bookReturnedQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(bookReturnedQueue).to(libraryEventsExchange).with(ROUTING_BOOK_RETURNED);
    }

    @Bean
    public Binding fineGeneratedBinding(Queue fineGeneratedQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(fineGeneratedQueue).to(libraryEventsExchange).with(ROUTING_FINE_GENERATED);
    }

    @Bean
    public Binding finePaidBinding(Queue finePaidQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(finePaidQueue).to(libraryEventsExchange).with(ROUTING_FINE_PAID);
    }

    @Bean
    public Binding bookAvailableBinding(Queue bookAvailableQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(bookAvailableQueue).to(libraryEventsExchange).with(ROUTING_BOOK_AVAILABLE);
    }

    // ---- Dead letter queues (one per event type, for inspection in RabbitMQ Management) ----

    @Bean
    public Queue bookBorrowedDlq() {
        return QueueBuilder.durable("notification.book-borrowed.dlq").build();
    }

    @Bean
    public Queue bookReturnedDlq() {
        return QueueBuilder.durable("notification.book-returned.dlq").build();
    }

    @Bean
    public Queue fineGeneratedDlq() {
        return QueueBuilder.durable("notification.fine-generated.dlq").build();
    }

    @Bean
    public Queue finePaidDlq() {
        return QueueBuilder.durable("notification.fine-paid.dlq").build();
    }

    @Bean
    public Queue bookAvailableDlq() {
        return QueueBuilder.durable("notification.book-available.dlq").build();
    }

    @Bean
    public Binding bookBorrowedDlqBinding(Queue bookBorrowedDlq, TopicExchange libraryDeadLetterExchange) {
        return BindingBuilder.bind(bookBorrowedDlq).to(libraryDeadLetterExchange).with(DLQ_ROUTING_BOOK_BORROWED);
    }

    @Bean
    public Binding bookReturnedDlqBinding(Queue bookReturnedDlq, TopicExchange libraryDeadLetterExchange) {
        return BindingBuilder.bind(bookReturnedDlq).to(libraryDeadLetterExchange).with(DLQ_ROUTING_BOOK_RETURNED);
    }

    @Bean
    public Binding fineGeneratedDlqBinding(Queue fineGeneratedDlq, TopicExchange libraryDeadLetterExchange) {
        return BindingBuilder.bind(fineGeneratedDlq).to(libraryDeadLetterExchange).with(DLQ_ROUTING_FINE_GENERATED);
    }

    @Bean
    public Binding finePaidDlqBinding(Queue finePaidDlq, TopicExchange libraryDeadLetterExchange) {
        return BindingBuilder.bind(finePaidDlq).to(libraryDeadLetterExchange).with(DLQ_ROUTING_FINE_PAID);
    }

    @Bean
    public Binding bookAvailableDlqBinding(Queue bookAvailableDlq, TopicExchange libraryDeadLetterExchange) {
        return BindingBuilder.bind(bookAvailableDlq).to(libraryDeadLetterExchange).with(DLQ_ROUTING_BOOK_AVAILABLE);
    }

    // ---- JSON conversion (shared cross-service type-id mapping) ----

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper objectMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(objectMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();
        typeMapper.setIdClassMapping(EVENT_TYPE_MAPPING);
        typeMapper.setTrustedPackages("*");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    // ---- Retry-enabled listener container factory ----

    @Bean
    public SimpleRabbitListenerContainerFactory retryContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            @Value("${library.notification.retry.max-attempts:3}") int maxAttempts,
            @Value("${library.notification.retry.backoff-ms:2000}") long backoffMs) {

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        factory.setAdviceChain(retryInterceptor(maxAttempts, backoffMs));
        return factory;
    }

    private StatelessRetryOperationsInterceptor retryInterceptor(
        int maxAttempts, long backoffMs) {

    return RetryInterceptorBuilder.stateless()
            .configureRetryPolicy(policy -> policy
                    .maxRetries(maxAttempts - 1L)
                    .delay(java.time.Duration.ofMillis(backoffMs))
                    .multiplier(1.0))
            .recoverer(new RejectAndDontRequeueRecoverer())
            .build();
}
}
