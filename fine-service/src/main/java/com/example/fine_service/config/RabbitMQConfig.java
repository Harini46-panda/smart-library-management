package com.example.fine_service.config;

import com.example.fine_service.event.BookOverdueEvent;
import com.example.fine_service.event.BookReturnedEvent;
import com.example.fine_service.event.FineGeneratedEvent;
import com.example.fine_service.event.FinePaidEvent;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.DefaultJacksonJavaTypeMapper;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "library.events";

    public static final String ROUTING_BOOK_RETURNED = "book.returned";
    public static final String ROUTING_BOOK_OVERDUE = "book.overdue";
    public static final String ROUTING_FINE_GENERATED = "fine.generated";
    public static final String ROUTING_FINE_PAID = "fine.paid";

    public static final String QUEUE_BOOK_RETURNED = "fine.book-returned.queue";
    public static final String QUEUE_BOOK_OVERDUE = "fine.book-overdue.queue";

    @Bean
    public TopicExchange libraryEventsExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue bookReturnedQueue() {
        return QueueBuilder.durable(QUEUE_BOOK_RETURNED).build();
    }

    @Bean
    public Queue bookOverdueQueue() {
        return QueueBuilder.durable(QUEUE_BOOK_OVERDUE).build();
    }

    @Bean
    public Binding bookReturnedBinding(Queue bookReturnedQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(bookReturnedQueue).to(libraryEventsExchange).with(ROUTING_BOOK_RETURNED);
    }

    @Bean
    public Binding bookOverdueBinding(Queue bookOverdueQueue, TopicExchange libraryEventsExchange) {
        return BindingBuilder.bind(bookOverdueQueue).to(libraryEventsExchange).with(ROUTING_BOOK_OVERDUE);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper objectMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(objectMapper);
        DefaultJacksonJavaTypeMapper typeMapper = new DefaultJacksonJavaTypeMapper();

        Map<String, Class<?>> typeMapping = new HashMap<>();
        typeMapping.put("bookReturnedEvent", BookReturnedEvent.class);
        typeMapping.put("bookOverdueEvent", BookOverdueEvent.class);
        typeMapping.put("fineGeneratedEvent", FineGeneratedEvent.class);
        typeMapping.put("finePaidEvent", FinePaidEvent.class);

        typeMapper.setIdClassMapping(typeMapping);
        typeMapper.setTrustedPackages("*");
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }
}
