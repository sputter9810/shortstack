package io.github.sputter9810.shortstack.api;

import java.time.Clock;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

import io.github.sputter9810.shortstack.service.LinkNotFoundException;
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
        String timestamp = new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(clock.instant());
        return ResponseEntity.status(404).cacheControl(CacheControl.noStore()).body(
                new ApiError(timestamp, 404, "LINK_NOT_FOUND", failure.getMessage(), request.getRequestURI(), List.of()));
    }
}
