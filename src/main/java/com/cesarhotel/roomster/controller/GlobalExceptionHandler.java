package com.cesarhotel.roomster.controller;

import com.cesarhotel.roomster.exception.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<String> handleValidation(ValidationException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
