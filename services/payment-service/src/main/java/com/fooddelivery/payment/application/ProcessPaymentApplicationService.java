package com.fooddelivery.payment.application;

import com.fooddelivery.payment.domain.event.PaymentFailedEvent;
import com.fooddelivery.payment.domain.event.PaymentSuccessEvent;
import com.fooddelivery.payment.domain.model.Payment;
import com.fooddelivery.payment.domain.model.PaymentMethod;
import com.fooddelivery.payment.domain.model.PaymentResult;
import com.fooddelivery.payment.domain.model.PaymentStatus;
import com.fooddelivery.payment.domain.port.inbound.ProcessPaymentUseCase;
import com.fooddelivery.payment.domain.port.outbound.EventPublisher;
import com.fooddelivery.payment.domain.port.outbound.PaymentGateway;
import com.fooddelivery.payment.domain.port.outbound.PaymentRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Application Service: Processes payment transactions.
 * <p>
 * This acts as a Saga Participant in the Order Saga. It receives a command
 * to process a payment, communicates with the external Payment Gateway,
 * and publishes either a PaymentSuccessEvent or PaymentFailedEvent depending on the outcome.
 */
public class ProcessPaymentApplicationService implements ProcessPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentGateway paymentGateway;
    private final EventPublisher eventPublisher;

    public ProcessPaymentApplicationService(PaymentRepository paymentRepository,
                                             PaymentGateway paymentGateway,
                                             EventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.paymentGateway = paymentGateway;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Processes a payment for a given order.
     * <p>
     * This method ensures idempotency by checking if a successful payment already exists.
     * If the payment fails at the gateway, it will be marked as failed and a failure event is published.
     * 
     * @param orderId the ID of the order being paid for
     * @param customerId the ID of the customer making the payment
     * @param amount the total amount to charge
     * @param method the payment method (e.g. CREDIT_CARD)
     */
    @Override
    public void processPayment(UUID orderId, UUID customerId, BigDecimal amount, PaymentMethod method) {
        Optional<Payment> existingOpt = paymentRepository.findByOrderId(orderId);
        if (existingOpt.isPresent()) {
            Payment existing = existingOpt.get();
            if (existing.getStatus() == PaymentStatus.SUCCESS) {
                // Already successfully paid, do not re-process
                return;
            }
        }

        // Initialize or update payment record
        Payment payment = existingOpt.orElseGet(() -> {
            Payment newPayment = new Payment();
            newPayment.setId(UUID.randomUUID());
            newPayment.setOrderId(orderId);
            newPayment.setCustomerId(customerId);
            newPayment.setAmount(amount);
            newPayment.setCurrency("VND");
            newPayment.setMethod(method);
            newPayment.setStatus(PaymentStatus.PENDING);
            return newPayment;
        });

        payment.markProcessing();
        payment = paymentRepository.save(payment);

        try {
            // Charge payment via gateway
            PaymentResult result = paymentGateway.charge(orderId, amount, method);

            if (result.success()) {
                payment.markSuccess(result.transactionId());
                paymentRepository.save(payment);

                PaymentSuccessEvent successEvent = new PaymentSuccessEvent(
                        payment.getOrderId(),
                        payment.getId(),
                        payment.getAmount(),
                        payment.getProviderTransactionId()
                );
                eventPublisher.publishSuccess(successEvent);
            } else {
                payment.markFailed(result.failureReason());
                paymentRepository.save(payment);

                PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                        payment.getOrderId(),
                        payment.getId(),
                        payment.getFailureReason()
                );
                eventPublisher.publishFailure(failedEvent);
            }
        } catch (Exception ex) {
            String reason = ex.getMessage() != null ? ex.getMessage() : "Unexpected gateway error";
            payment.markFailed(reason);
            paymentRepository.save(payment);

            PaymentFailedEvent failedEvent = new PaymentFailedEvent(
                    payment.getOrderId(),
                    payment.getId(),
                    payment.getFailureReason()
            );
            eventPublisher.publishFailure(failedEvent);
        }
    }
}
