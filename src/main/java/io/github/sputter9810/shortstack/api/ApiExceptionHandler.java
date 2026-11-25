package io.github.sputter9810.shortstack.api;

import java.time.Clock;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

import io.github.sputter9810.shortstack.service.LinkNotFoundException;
import io.github.sputter9810.shortstack.service.InvalidLinkRequestException;
import io.github.sputter9810.shortstack.service.AliasConflictException;
import io.github.sputter9810.shortstack.service.LinkExpiredException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(LinkNotFoundException.class)
    public ResponseEntity<ApiError> missing(LinkNotFoundException failure, HttpServletRequest request) {
        return error(404, "LINK_NOT_FOUND", failure.getMessage(), List.of(), request);
    }

    @ExceptionHandler(InvalidLinkRequestException.class)
    public ResponseEntity<ApiError> invalid(InvalidLinkRequestException failure, HttpServletRequest request) {
        return error(400, "VALIDATION_FAILED", "Request validation failed.",
                List.of(new ApiError.Violation(failure.getField(), failure.getMessage())), request);
    }

    @ExceptionHandler(AliasConflictException.class)
    public ResponseEntity<ApiError> conflict(AliasConflictException failure, HttpServletRequest request) {
        return error(409, "ALIAS_CONFLICT", failure.getMessage(), List.of(), request);
    }

    @ExceptionHandler(LinkExpiredException.class)
    public ResponseEntity<ApiError> expired(LinkExpiredException failure, HttpServletRequest request) {
        return error(410, "LINK_EXPIRED", failure.getMessage(), List.of(), request);
    }

    private ResponseEntity<ApiError> error(int status, String code, String message,
                                          List<ApiError.Violation> violations, HttpServletRequest request) {
        String timestamp = new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(clock.instant());
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(
                new ApiError(timestamp, status, code, message, request.getRequestURI(), violations));
    }
}
