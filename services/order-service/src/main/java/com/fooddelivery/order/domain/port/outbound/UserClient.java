package com.fooddelivery.order.domain.port.outbound;

import java.util.UUID;

/**
 * Outbound port: Client for querying User Service.
 * Implemented by REST adapter (UserRestClient).
 *
 * NOTE: Pure Java interface — no framework imports.
 */
public interface UserClient {

    /**
     * Get basic user info for validation.
     * Returns null if user not found.
     */
    UserInfo getUserById(UUID userId);

    record UserInfo(UUID id, String email, String fullName, String role, boolean isActive) {}
}
