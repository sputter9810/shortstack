package io.github.sputter9810.shortstack.api;

import java.util.List;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApplicationErrorController implements ErrorController {
    private final ApiExceptionHandler errors;

    public ApplicationErrorController(ApiExceptionHandler errors) {
        this.errors = errors;
    }

    @RequestMapping("/error")
    public ResponseEntity<ApiError> error(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = statusAttribute instanceof Integer value && value >= 400 && value <= 599 ? value : 404;
        Object path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return errors.errorForPath(status, status == 404 ? "ROUTE_NOT_FOUND" : "INTERNAL_ERROR",
                status == 404 ? "The route was not found." : "The request could not be completed.", List.of(),
                path instanceof String original ? original : request.getRequestURI());
    }
}
