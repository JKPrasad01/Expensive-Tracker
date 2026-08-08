package com.expensetracker.exception;

public class ActionAlreadyExistsException extends RuntimeException {

    public ActionAlreadyExistsException(String message) {
        super(message);
    }
}