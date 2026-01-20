package com.example.auction.common.exception;


public class UnauthorizedAccessException extends RuntimeException {
    private final String errorCode;

    public UnauthorizedAccessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public UnauthorizedAccessException(String message) {
        super(message);
        this.errorCode = "NOT_ALLOWED";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
