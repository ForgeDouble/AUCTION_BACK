package com.example.auction.bid.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorMessageDto {
    private String message;
    private String errorCode;
    private Long timestamp;

    public ErrorMessageDto(String message) {
        this.message = message;
        this.errorCode = "UNKNOWN_ERROR";
        this.timestamp = System.currentTimeMillis();
    }
}