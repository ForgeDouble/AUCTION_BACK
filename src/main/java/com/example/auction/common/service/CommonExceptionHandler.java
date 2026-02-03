package com.example.auction.common.service;

import com.example.auction.common.dto.CommonErrorDto;
import com.example.auction.common.exception.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Arrays;

@Slf4j
@ControllerAdvice
public class CommonExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<CommonErrorDto> entityNotFoundHandler(EntityNotFoundException e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                "DATA_NOT_FOUND",
                "요청한 자원이 존재하지 않습니다. (" + e.getMessage() + ")"
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.NOT_FOUND);
    }

    // 나머지 예외 핸들러도 여기에 포함되어야 합니다.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<CommonErrorDto> illegalArgumentHandler(IllegalArgumentException e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                "BAD_REQUEST",
                "잘못된 요청입니다. (" + e.getMessage() + ")"
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<CommonErrorDto> illegalStateHandler(IllegalStateException e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                "BAD_REQUEST",
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
                "BAD_REQUEST",
                errorMessage.toString()
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonErrorDto> generalExceptionHandler(Exception e) {
        log.error("[INTERNAL_ERROR] message={}, cause={}",
                e.getMessage(),
                e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none",
                e);
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                "INTERNAL_SERVER_ERROR",
                "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
        );
        return new ResponseEntity<>(commonErrorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<CommonErrorDto> unsupportedOperationExceptionHandler(UnsupportedOperationException e) {
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                "METHOD_NOT_ALLOWED",
                "지원하지 않는 작업입니다. (" + e.getMessage() + ")"
        );
        e.printStackTrace();
        return new ResponseEntity<>(commonErrorDto, HttpStatus.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<CommonErrorDto> ResourceNotFoundHandler(ResourceNotFoundException e) {
        log.error("[{}] message={}, cause={}",
                e.getErrorCode(),
                e.getMessage(),
                e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none",
                e);
        CommonErrorDto commonErrorDto = new CommonErrorDto(
                e.getErrorCode(),
                e.getMessage()
        );
        return new ResponseEntity<>(commonErrorDto, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<CommonErrorDto> unauthorizedHandler(UnauthorizedAccessException e) {
        log.error("[{}] message={}, cause={}",
                e.getErrorCode(),
                e.getMessage(),
                e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none",
                e);
        CommonErrorDto error = new CommonErrorDto(
                e.getErrorCode(),
                e.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<CommonErrorDto> AccountSuspendedHandler(AccountSuspendedException e) {
        log.error("[{}] message={}, cause={}",
                e.getErrorCode(),
                e.getMessage(),
                e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none",
                e);
        CommonErrorDto error = new CommonErrorDto(
                e.getErrorCode(),
                e.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<CommonErrorDto> BadRequestHandler(BadRequestException e) {
        log.error("[{}] message={}, cause={}",
                e.getErrorCode(),
                e.getMessage(),
                e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none",
                e);
        CommonErrorDto error = new CommonErrorDto(
                e.getErrorCode(),
                e.getMessage()
        );
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InternalErrorException.class)
    public ResponseEntity<CommonErrorDto> InternalErrorHandler(InternalErrorException e) {
        log.error("[{}] message={}, cause={}",
                e.getErrorCode(),
                e.getMessage(),
                e.getCause() != null ? e.getCause().getClass().getSimpleName() : "none",
                e);

        CommonErrorDto error = new CommonErrorDto(
                "INTERNAL_SERVER_ERROR",
                "서버 내부에서 오류가 발생했습니다. 관리자에게 문의해주세요."
        );
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
