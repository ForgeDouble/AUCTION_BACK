package com.example.auction.bid.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidResponseDto {
    private boolean success;
    private String message;
    private BidEvent data;
    private String errorCode;
    private Long timestamp;

    public static BidResponseDto success(BidEvent data) {
        return BidResponseDto.builder()
                .success(true)
                .message("입찰이 완료되었습니다.")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static BidResponseDto error(String message, String errorCode) {
        return BidResponseDto.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
