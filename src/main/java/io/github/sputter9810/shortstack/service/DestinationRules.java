package io.github.sputter9810.shortstack.service;

import java.net.URI;
import java.net.URISyntaxException;

public final class DestinationRules {
    private DestinationRules() {
    }

    public static void validate(String value) {
        try {
            if (value == null || value.isEmpty() || value.length() > 2048
                    || value.chars().anyMatch(c -> c <= 32 || c >= 127 || c == '\\')) {
                throw new IllegalArgumentException();
            }
            URI uri = new URI(value).parseServerAuthority();
            if (!uri.isAbsolute() || uri.isOpaque() || uri.getHost() == null || uri.getRawUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getPort() == 0 || uri.getPort() > 65535 || uri.getRawAuthority().endsWith(":")) {
                throw new IllegalArgumentException();
            }
        } catch (URISyntaxException | IllegalArgumentException failure) {
            throw new InvalidLinkRequestException("destinationUrl",
                    "Must be an absolute HTTP or HTTPS URL with a host, valid port and no user information, using at most 2048 ASCII characters.");
        }
    }
}
