package com.fooddelivery.payment.adapter.outbound.gateway;

import com.fooddelivery.payment.domain.model.PaymentMethod;
import com.fooddelivery.payment.domain.model.PaymentResult;
import com.fooddelivery.payment.domain.port.outbound.PaymentGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Random;
import java.util.UUID;

public class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);
    private final Random random = new Random();

    @Override
    public PaymentResult charge(UUID orderId, BigDecimal amount, PaymentMethod method) {
        log.info("Processing mock payment for order {}, amount {} VND, method {}", orderId, amount, method);

        // Simulate network delay
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 100% success rate for reliable E2E testing
        String transactionId = "mock_tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("Mock payment SUCCESS for order {}. TxID: {}", orderId, transactionId);
        return new PaymentResult(true, transactionId, null);
    }
}
