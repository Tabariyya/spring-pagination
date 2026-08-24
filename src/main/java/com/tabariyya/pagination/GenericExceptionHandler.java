package com.tabariyya.pagination;

import com.tabariyya.utils.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class GenericExceptionHandler {

    @ExceptionHandler(GenericQueryDslException.class)
    public ResponseEntity<String> handleGenericQueryDslException(GenericQueryDslException ex) {
        Throwable rootCause = ExceptionUtils.getRootCause(ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(rootCause.getMessage());
    }

    @ExceptionHandler(InvalidFilterValueException.class)
    public ResponseEntity<String> handleInvalidFilterValueException(InvalidFilterValueException ex) {
        Throwable rootCause = ExceptionUtils.getRootCause(ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(rootCause.getMessage());
    }

    @ExceptionHandler(InvalidCursorException.class)
    public ResponseEntity<String> handleInvalidCursorException(InvalidCursorException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(UnknownResponseFieldException.class)
    public ResponseEntity<String> handleUnknownResponseFieldException(UnknownResponseFieldException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(InvalidAggregationException.class)
    public ResponseEntity<String> handleInvalidAggregationException(InvalidAggregationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    @ExceptionHandler(CursorParameterConflictException.class)
    public ResponseEntity<String> handleCursorParameterConflictException(CursorParameterConflictException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

}
