package com.fooddelivery.order.exception;

/**
 * Base exception for resource not found (HTTP 404).
 * Usage: throw new ResourceNotFoundException("USER_NOT_FOUND", "User with id 'x' was not found");
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
