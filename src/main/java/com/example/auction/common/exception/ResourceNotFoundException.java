package com.example.auction.common.exception;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resourceName) {
        super(resourceName + "을(를) 찾을 수 없습니다.");
    }

    public ResourceNotFoundException(String resourceName, Long resourceId) {
        super(resourceName + "을(를) 찾을 수 없습니다. id=" + resourceId);
    }
}
