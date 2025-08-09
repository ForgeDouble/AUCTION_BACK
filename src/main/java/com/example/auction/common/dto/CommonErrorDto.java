package com.example.auction.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommonErrorDto {
    private int status_code;
    private String error_message;

    public CommonErrorDto(HttpStatus httpStatus, String error_message) {
        this.status_code = httpStatus.value();
        this.error_message = error_message;
    }
}
