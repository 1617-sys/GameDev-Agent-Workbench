package com.example.gameworkbench.common.handler;

import com.example.gameworkbench.common.ApiResponse;
import com.example.gameworkbench.common.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
        String message = "请求参数不合法";

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

        return ApiResponse.error(40001, message);
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ApiResponse.error(50000, "系统异常");
    }
}
