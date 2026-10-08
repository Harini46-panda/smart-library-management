package com.example.borrowing_service.config;

import com.example.borrowing_service.event.BookBorrowedEvent;
import com.example.borrowing_service.event.BookOverdueEvent;
import com.example.borrowing_service.event.BookReturnedEvent;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Borrowing Service only ever publishes onto the shared "library.events"
 * topic exchange (book.borrowed / book.returned / book.overdue) - it has no
 * queues or listeners of its own, so it just needs the exchange declared
 * (durable, idempotent declare) and the same cross-service type mapping used
 * everywhere else so consumers can deserialize its events.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "library.events";

    public static final String ROUTING_BOOK_BORROWED = "book.borrowed";
    public static final String ROUTING_BOOK_RETURNED = "book.returned";
    public static final String ROUTING_BOOK_OVERDUE = "book.overdue";

    @Bean
    public TopicExchange libraryEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper objectMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(objectMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();

        Map<String, Class<?>> typeMapping = new HashMap<>();
        typeMapping.put("bookBorrowedEvent", BookBorrowedEvent.class);
        typeMapping.put("bookReturnedEvent", BookReturnedEvent.class);
        typeMapping.put("bookOverdueEvent", BookOverdueEvent.class);

        typeMapper.setIdClassMapping(typeMapping);
        typeMapper.setTrustedPackages("*");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
