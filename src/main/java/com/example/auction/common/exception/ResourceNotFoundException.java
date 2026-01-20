package com.example.auction.common.exception;

public class ResourceNotFoundException extends RuntimeException {
    private final String errorCode;

    public ResourceNotFoundException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.errorCode = "DATA_NOT_FOUND";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
