package io.github.sputter9810.shortstack.api;

public record LinkMetadata(String code, String shortUrl, String destinationUrl,
                           String createdAt, String expiresAt, boolean expired) {
}
