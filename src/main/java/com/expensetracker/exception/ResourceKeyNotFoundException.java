package com.expensetracker.exception;

public class ResourceKeyNotFoundException extends RuntimeException {
    public ResourceKeyNotFoundException(String message) {
        super(message);
    }
}
