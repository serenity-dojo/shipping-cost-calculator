package com.example.shipping.model;

/**
 * Thrown when a shipping request's weight falls outside the valid range
 * (greater than 0kg and at most 50kg). Signals a client validation error.
 */
public class InvalidWeightException extends RuntimeException {

    public InvalidWeightException(String message) {
        super(message);
    }
}
