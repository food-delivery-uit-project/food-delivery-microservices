package com.fooddelivery.order.exception;

/**
 * Exception for duplicate resource (HTTP 409).
 * Usage: throw new DuplicateResourceException("USER_EMAIL_EXISTS", "Email already registered");
 */
public class DuplicateResourceException extends RuntimeException {
    private final String errorCode;

    public DuplicateResourceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
