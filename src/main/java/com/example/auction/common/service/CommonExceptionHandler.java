package com.example.auction.common.service;

import com.example.auction.common.dto.CommonErrorDto;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CommonExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<CommonErrorDto> entityNotFoundHandler(EntityNotFoundException e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                HttpStatus.NOT_FOUND,
                "요청한 자원이 존재하지 않습니다. (" + e.getMessage() + ")"
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.NOT_FOUND);
    }

    // 나머지 예외 핸들러도 여기에 포함되어야 합니다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonErrorDto> illegalArgumentHandler(IllegalArgumentException e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                HttpStatus.BAD_REQUEST,
                "잘못된 요청입니다. (" + e.getMessage() + ")"
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CommonErrorDto> illegalStateHandler(IllegalStateException e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                HttpStatus.BAD_REQUEST,
                "상태 오류가 발생했습니다. (" + e.getMessage() + ")"
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonErrorDto> methodArgumentNotValidHandler(MethodArgumentNotValidException e) {

        StringBuilder errorMessage = new StringBuilder("입력값이 유효하지 않습니다.");
        e.getBindingResult().getAllErrors().forEach(error -> {
            errorMessage.append(" ").append(error.getDefaultMessage());
        });
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                HttpStatus.BAD_REQUEST,
                errorMessage.toString()
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonErrorDto> generalExceptionHandler(Exception e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<CommonErrorDto> unsupportedOperationExceptionHandler(UnsupportedOperationException e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                HttpStatus.METHOD_NOT_ALLOWED,
                "지원하지 않는 작업입니다. (" + e.getMessage() + ")"
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.METHOD_NOT_ALLOWED);
    }

}
