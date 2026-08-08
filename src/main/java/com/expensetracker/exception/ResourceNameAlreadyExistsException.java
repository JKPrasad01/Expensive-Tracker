package com.expensetracker.exception;

public class ResourceNameAlreadyExistsException extends RuntimeException {
    public ResourceNameAlreadyExistsException(String message) {
        super(message);
    }
}
