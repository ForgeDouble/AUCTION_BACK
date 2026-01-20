package com.example.auction.common.exception;

public class AccountSuspendedException extends RuntimeException {

    private final String errorCode;
    private final String suspendedUntil;

    public AccountSuspendedException(String message) {
        super(message);
        this.errorCode = "ACCOUNT_SUSPENDED";
        this.suspendedUntil = null;
    }

//    public AccountSuspendedException(String errorCode, String message) {
//        super(message);
//        this.errorCode = errorCode;
//        this.suspendedUntil = null;
//    }

    public AccountSuspendedException(String errorCode, String message, String suspendedUntil) {
        super(message);
        this.errorCode = errorCode;
        this.suspendedUntil = suspendedUntil;
    }

    public AccountSuspendedException(String message, String suspendedUntil) {
        super(message);
        this.errorCode = "ACCOUNT_SUSPENDED";
        this.suspendedUntil = suspendedUntil;
    }



    public String getErrorCode() {
        return errorCode;
    }

    public String getSuspendedUntil() {
        return suspendedUntil;
    }
}
