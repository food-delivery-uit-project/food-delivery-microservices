package com.fooddelivery.order.application;

/**
 * Resource not found exception for application layer.
 * Re-used by application services when order is not found.
 */
public class ResourceNotFoundException extends RuntimeException {

    private final String errorCode;

    public ResourceNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
