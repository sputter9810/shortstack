package io.github.sputter9810.shortstack.api;

import java.time.Clock;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;

import io.github.sputter9810.shortstack.service.LinkNotFoundException;
import io.github.sputter9810.shortstack.service.InvalidLinkRequestException;
import io.github.sputter9810.shortstack.service.AliasConflictException;
import io.github.sputter9810.shortstack.service.LinkExpiredException;
import io.github.sputter9810.shortstack.service.InvalidCodeException;
import io.github.sputter9810.shortstack.service.CodeGenerationExhaustedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
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

    @ExceptionHandler(InvalidCodeException.class)
    public ResponseEntity<ApiError> code(InvalidCodeException failure, HttpServletRequest request) {
        return error(failure.isReserved() ? 404 : 400, failure.isReserved() ? "ROUTE_NOT_FOUND" : "INVALID_CODE",
                failure.getMessage(), List.of(), request);
    }

    @ExceptionHandler(CodeGenerationExhaustedException.class)
    public ResponseEntity<ApiError> exhausted(CodeGenerationExhaustedException failure, HttpServletRequest request) {
        return error(503, "CODE_GENERATION_EXHAUSTED", "Unable to allocate a short code. Try again.", List.of(), request);
    }

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class})
    public ResponseEntity<ApiError> unavailable(Exception failure, HttpServletRequest request) {
        return error(503, "SERVICE_UNAVAILABLE", "The service is temporarily unavailable.", List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception failure, HttpServletRequest request) {
        logger.error("Unhandled application failure", failure);
        return error(500, "INTERNAL_ERROR", "An internal error occurred.", List.of(), request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception failure, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        HttpServletRequest servlet = ((ServletWebRequest) request).getRequest();
        String code = switch (status.value()) {
            case 400 -> "INVALID_REQUEST";
            case 404 -> "ROUTE_NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 406 -> "NOT_ACCEPTABLE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            default -> "INTERNAL_ERROR";
        };
        ResponseEntity<ApiError> result = error(status.value(), code,
                status.value() == 400 ? "The request body is invalid." : "The request could not be completed.", List.of(), servlet);
        HttpHeaders combined = new HttpHeaders();
        combined.putAll(headers);
        combined.putAll(result.getHeaders());
        return new ResponseEntity<>(result.getBody(), combined, status);
    }

    public ResponseEntity<ApiError> error(int status, String code, String message,
                                          List<ApiError.Violation> violations, HttpServletRequest request) {
        return errorForPath(status, code, message, violations, request.getRequestURI());
    }

    public ResponseEntity<ApiError> errorForPath(int status, String code, String message,
                                                List<ApiError.Violation> violations, String path) {
        String timestamp = new DateTimeFormatterBuilder().appendInstant(6).toFormatter().format(clock.instant());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).body(
                new ApiError(timestamp, status, code, message, path, violations));
    }
}
