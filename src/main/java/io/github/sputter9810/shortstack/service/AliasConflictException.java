package io.github.sputter9810.shortstack.service;

public class AliasConflictException extends RuntimeException {
    public AliasConflictException(Throwable cause) {
        super("The custom alias is already in use.", cause);
    }
}
