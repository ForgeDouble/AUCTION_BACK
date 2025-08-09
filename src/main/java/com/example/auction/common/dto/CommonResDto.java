package com.example.auction.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommonResDto {
    private int status_code;
    private String status_message;
    private Object result;

    public CommonResDto(HttpStatus httpStatus, String status_message, Object result) {
        this.status_code = httpStatus.value();
        this.status_message = status_message;
        this.result = result;
    }
}
