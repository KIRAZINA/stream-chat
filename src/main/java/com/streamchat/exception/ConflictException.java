package com.streamchat.exception;

/**
 * Thrown when an optimistic-lock retry exhausts its single retry and the
 * conflicting state could not be reconciled. Maps to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}