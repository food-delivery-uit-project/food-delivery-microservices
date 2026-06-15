package com.fooddelivery.order.exception;

/**
 * Exception for business logic errors (HTTP 422).
 * Usage: throw new BusinessException("RESTAURANT_CLOSED", "Restaurant is currently closed");
 */
public class BusinessException extends RuntimeException {
    private final String errorCode;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
