package com.fooddelivery.order.domain.port.inbound;

import java.util.UUID;

/**
 * Inbound port: Use case for cancelling an order.
 */
public interface CancelOrderUseCase {

    void execute(UUID orderId, UUID requesterId);
}
