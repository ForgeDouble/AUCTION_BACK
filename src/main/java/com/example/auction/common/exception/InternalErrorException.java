package com.example.auction.common.exception;

public class InternalErrorException extends RuntimeException{
    private final String errorCode;

    public InternalErrorException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InternalErrorException(String message) {
        super(message);
        this.errorCode = "INTERNAL_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
