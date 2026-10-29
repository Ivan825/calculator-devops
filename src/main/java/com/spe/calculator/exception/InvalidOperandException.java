package com.spe.calculator.exception;

public class InvalidOperandException extends RuntimeException {

    public InvalidOperandException(String message) {
        super(message);
    }
}
