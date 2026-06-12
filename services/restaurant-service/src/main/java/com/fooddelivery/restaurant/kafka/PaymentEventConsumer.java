package com.fooddelivery.restaurant.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    @KafkaListener(topics = "payment-events", groupId = "restaurant-service-group")
    public void handlePaymentEvent(PaymentEvent event) {
        log.info("Received payment event: {}", event);
        if ("PaymentSuccess".equalsIgnoreCase(event.getType())) {
            log.info("[Restaurant Service] Payment successful for Order ID: {}. Preparing food...", event.getOrderId());
        }
    }
}
