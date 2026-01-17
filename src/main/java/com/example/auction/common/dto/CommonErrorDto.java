package com.example.auction.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonErrorDto {
    private String statusCode;
    private String errorMessage;
    private String additionalInfo;

    public CommonErrorDto(String statusCode, String errorMessage) {
        this.statusCode = statusCode;
        this.errorMessage = errorMessage;
        this.additionalInfo = null;

    }
}
