package io.github.sputter9810.shortstack.service;

import java.util.Locale;
import java.util.Set;

public final class CodeRules {
    private static final Set<String> RESERVED = Set.of("api", "error", "actuator", "health", "metrics", "docs",
            "swagger-ui", "v3", "favicon.ico", "robots.txt");

    private CodeRules() {
    }

    public static boolean valid(String code) {
        return code != null && code.matches("[A-Za-z0-9_-]{3,32}");
    }

    public static boolean reserved(String code) {
        return RESERVED.contains(code.toLowerCase(Locale.ROOT));
    }

    public static void validateAlias(String alias) {
        if (!valid(alias) || reserved(alias)) {
            throw new InvalidLinkRequestException("customAlias",
                    "Must contain 3 to 32 ASCII letters, digits, underscores or hyphens and must not be reserved.");
        }
    }

    public static void validateLookup(String code) {
        if (!valid(code)) {
            throw new InvalidCodeException(false);
        }
    }
}
