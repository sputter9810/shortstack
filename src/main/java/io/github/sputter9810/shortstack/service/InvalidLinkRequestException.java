package io.github.sputter9810.shortstack.service;

public class InvalidLinkRequestException extends RuntimeException {
    private final String field;

    public InvalidLinkRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
