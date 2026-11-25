package io.github.sputter9810.shortstack.service;

public class LinkNotFoundException extends RuntimeException {
    public LinkNotFoundException() {
        super("The link was not found.");
    }
}
