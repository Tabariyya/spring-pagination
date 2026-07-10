package com.tabariyya.pagination;

public class InvalidFilterValueException extends RuntimeException {
    public InvalidFilterValueException(String message, Throwable cause) {
        super(message, cause);
    }
}
