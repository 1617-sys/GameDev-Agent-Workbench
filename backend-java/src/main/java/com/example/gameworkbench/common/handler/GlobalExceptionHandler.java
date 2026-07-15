package com.example.gameworkbench.common.handler;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.common.enums.ErrorCode;
import com.example.gameworkbench.common.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.error(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class
    })
    public ApiResponse<Void> handleValidationException(Exception exception) {
        String message = ErrorCode.INVALID_PARAM.getMessage();

        if (exception instanceof MethodArgumentNotValidException manve) {
            FieldError fieldError = manve.getBindingResult().getFieldError();
            if (fieldError != null && fieldError.getDefaultMessage() != null) {
                message = fieldError.getDefaultMessage();
            }
        } else if (exception instanceof BindException bindException) {
            FieldError fieldError = bindException.getBindingResult().getFieldError();
            if (fieldError != null && fieldError.getDefaultMessage() != null) {
                message = fieldError.getDefaultMessage();
            }
        } else if (exception instanceof ConstraintViolationException cve) {
            ConstraintViolation<?> violation = cve.getConstraintViolations()
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (violation != null && violation.getMessage() != null) {
                message = violation.getMessage();
            }
        }

        return ApiResponse.error(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ApiResponse<Void> handleUnreadableRequest(HttpMessageNotReadableException exception) {
        return ApiResponse.error(ErrorCode.INVALID_PARAM.getCode(), ErrorCode.INVALID_PARAM.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("[GlobalException] unhandled exception errorCode={} exceptionType={}",
                ErrorCode.SYSTEM_ERROR.getCode(), exception.getClass().getSimpleName());
        return ApiResponse.error(
                ErrorCode.SYSTEM_ERROR.getCode(),
                ErrorCode.SYSTEM_ERROR.getMessage()
        );
    }
}
