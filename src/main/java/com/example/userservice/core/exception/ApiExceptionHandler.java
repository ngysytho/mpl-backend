package com.example.userservice.core.exception;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import java.net.URI;
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ServiceException.class)
    ResponseEntity<ProblemDetail> service(ServiceException e, HttpServletRequest r) { return problem(e.status(), e.code(), e.getMessage(), r); }
    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class,
        HttpMessageNotReadableException.class, MissingRequestHeaderException.class, MethodArgumentTypeMismatchException.class,
        HandlerMethodValidationException.class, MissingServletRequestParameterException.class})
    ResponseEntity<ProblemDetail> validation(Exception e, HttpServletRequest r) {
        return problem(400, "VALIDATION_FAILED", "Invalid request fields, query parameters, or headers", r);
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> conflict(Exception e, HttpServletRequest r) {
        return problem(409, "DATA_CONFLICT", "Email or identity already reserved; reuse the original idempotency key", r);
    }
    @ExceptionHandler({PessimisticLockingFailureException.class, OptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> concurrent(Exception e, HttpServletRequest r) {
        return problem(409, "CONCURRENT_CHANGE", "Concurrent operation; retry the same request", r);
    }
    @ExceptionHandler({DataAccessResourceFailureException.class, QueryTimeoutException.class})
    ResponseEntity<ProblemDetail> unavailable(Exception e, HttpServletRequest r) {
        return problem(503, "DATABASE_UNAVAILABLE", "Database operation unavailable; retry safely with the original idempotency key", r);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception e, HttpServletRequest r) {
        if (e instanceof org.springframework.web.ErrorResponse error)
            return problem(error.getStatusCode().value(), "HTTP_" + error.getStatusCode().value(), "Request could not be processed", r);
        if (e instanceof org.springframework.security.access.AccessDeniedException)
            return problem(403, "FORBIDDEN", "Request could not be authorized", r);
        log.error("Request failed, traceId={}, failureType={}", r.getAttribute("traceId"), e.getClass().getSimpleName());
        return problem(500, "INTERNAL_ERROR", "Unexpected server error", r);
    }
    private ResponseEntity<ProblemDetail> problem(int status, String code, String detail, HttpServletRequest request) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status), detail);
        p.setType(URI.create("urn:mpl:error:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        p.setProperty("code", code); p.setProperty("traceId", request.getAttribute("traceId"));
        var builder = ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (status == 503) builder.header("Retry-After", "10");
        if (status == 429) builder.header("Retry-After", "3600");
        return builder.body(p);
    }
}
