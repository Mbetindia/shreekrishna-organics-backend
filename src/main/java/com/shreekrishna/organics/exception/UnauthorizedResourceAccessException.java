package com.shreekrishna.organics.exception;

public class UnauthorizedResourceAccessException extends RuntimeException {

    public UnauthorizedResourceAccessException(String message) {
        super(message);
    }
}