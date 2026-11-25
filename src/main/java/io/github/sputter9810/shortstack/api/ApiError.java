package io.github.sputter9810.shortstack.api;

import java.util.List;

public record ApiError(String timestamp, int status, String error, String message,
                       String path, List<Violation> violations) {
    public record Violation(String field, String message) {
    }
}
