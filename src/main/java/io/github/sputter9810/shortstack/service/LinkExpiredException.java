package io.github.sputter9810.shortstack.service;

public class LinkExpiredException extends RuntimeException {
    public LinkExpiredException() {
        super("The link has expired.");
    }
}
