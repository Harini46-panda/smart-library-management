package com.example.fine_service.messaging;

import com.example.fine_service.config.RabbitMQConfig;
import com.example.fine_service.entity.Fine;
import com.example.fine_service.event.FineGeneratedEvent;
import com.example.fine_service.event.FinePaidEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(EventPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishFineGenerated(Fine fine) {
        FineGeneratedEvent event = new FineGeneratedEvent(
                UUID.randomUUID().toString(), fine.getId(), fine.getBorrowingId(), fine.getMemberId(), fine.getAmount());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_FINE_GENERATED, event);
        log.info("Published FineGenerated event for fineId={}", fine.getId());
    }

    public void publishFinePaid(Fine fine) {
        FinePaidEvent event = new FinePaidEvent(
                UUID.randomUUID().toString(), fine.getId(), fine.getMemberId(), fine.getAmount());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_FINE_PAID, event);
        log.info("Published FinePaid event for fineId={}", fine.getId());
    }
}
