package com.chat.exception;

import com.chat.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<Result<?>> handleCustomException(CustomException e) {

        ErrorCode errorCode = e.getErrorCode();

        if (errorCode == ErrorCode.SERVER_BUSY) {
            log.warn("[429] SERVER_BUSY - BCrypt semaphore timeout");
            return ResponseEntity
                    .status(errorCode.getStatus())
                    .header("Retry-After", "1")
                    .body(Result.builder()
                            .status(errorCode.getStatus())
                            .message(errorCode.getErrorMessage())
                            .build());
        }

        return ResponseEntity
                .status(errorCode.getStatus())
                .body(Result.builder()
                        .status(errorCode.getStatus())
                        .message(errorCode.getErrorMessage())
                        .build());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<?>> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {

        ErrorCode errorCode = ErrorCode.MISSING_REQUEST_PARAMETER;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(Result.builder()
                        .status(errorCode.getStatus())
                        .message(errorCode.getErrorMessage())
                        .build());
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<Result<?>> handleServletRequestBindingException(ServletRequestBindingException e) {

        if (e.getClass() != ServletRequestBindingException.class) {
            return handleUnexpectedException(e);
        }

        ErrorCode errorCode = ErrorCode.USER_NOT_AUTHENTICATED;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(Result.builder()
                        .status(errorCode.getStatus())
                        .message(errorCode.getErrorMessage())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<?>> handleException(Exception e) {
        return handleUnexpectedException(e);
    }

    private ResponseEntity<Result<?>> handleUnexpectedException(Exception e) {
        log.error("HTTP 처리 실패: 예상치 못한 예외", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.builder()
                        .status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .message("서버 내부 오류가 발생했습니다.")
                        .build());
    }
}
