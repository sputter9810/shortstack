package io.github.sputter9810.shortstack.service;

public class InvalidCodeException extends RuntimeException {
    private final boolean reserved;

    public InvalidCodeException(boolean reserved) {
        super(reserved ? "The route was not found." : "The code is invalid.");
        this.reserved = reserved;
    }

    public boolean isReserved() {
        return reserved;
    }
}
