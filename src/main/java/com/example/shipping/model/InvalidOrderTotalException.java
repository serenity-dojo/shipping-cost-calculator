package com.example.shipping.model;

public class InvalidOrderTotalException extends RuntimeException {

    public InvalidOrderTotalException(String message) {
        super(message);
    }
}
