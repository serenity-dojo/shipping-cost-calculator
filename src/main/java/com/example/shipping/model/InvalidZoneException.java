package com.example.shipping.model;

public class InvalidZoneException extends RuntimeException {

    public InvalidZoneException(String message) {
        super(message);
    }
}
