package com.coderockr.investments.application;

public final class InvalidRequest extends RuntimeException {
    public InvalidRequest(String message) {
        super(message);
    }
}
