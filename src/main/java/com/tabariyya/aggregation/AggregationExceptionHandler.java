package com.tabariyya.aggregation;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class AggregationExceptionHandler {

    @ExceptionHandler(InvalidAggregationException.class)
    public ResponseEntity<String> handleInvalidAggregationException(InvalidAggregationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
}
