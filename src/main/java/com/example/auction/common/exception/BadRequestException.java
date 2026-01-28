package com.example.auction.common.exception;

public class BadRequestException extends RuntimeException{
    private final String errorCode;

    public BadRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public BadRequestException(String message) {
        super(message);
        this.errorCode = "BAD_REQUEST";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
