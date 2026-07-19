package com.tabariyya.pagination;

public class InvalidCursorException extends RuntimeException {
    public InvalidCursorException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidCursorException(String message) {
        super(message);
    }
}
