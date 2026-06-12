package com.expensive.Expensive.Tracker.exception;

public class ActionAlreadyExistsException extends RuntimeException {

    public ActionAlreadyExistsException(String message) {
        super(message);
    }
}