package com.yowyob.tiibntick.core.trust.adapter.in.web;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler for tnt-trust-core REST adapters.
 * Maps Bean Validation failures to RFC 7807 Problem Detail 400 responses,
 * and anything unforeseen to a generic 500 without leaking internals.
 *
 * @author MANFOUO Braun
 */
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.trust.adapter.in.web")
public class TrustExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TrustExceptionHandler.class);

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ProblemDetail> handleValidationError(final WebExchangeBindException ex) {
        final String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Request validation failed: {}", detail);
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create("urn:tiibntick:trust:validation-error"));
        pd.setTitle("Validation Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ProblemDetail> handleConstraintViolation(final ConstraintViolationException ex) {
        final String detail = ex.getConstraintViolations().stream()
                .map(cv -> cv.getPropertyPath() + ": " + cv.getMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        log.warn("Constraint violation: {}", detail);
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create("urn:tiibntick:trust:validation-error"));
        pd.setTitle("Validation Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(NullPointerException.class)
    public Mono<ProblemDetail> handleNullPointer(final NullPointerException ex) {
        log.error("Unexpected null reference in trust module", ex);
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(URI.create("urn:tiibntick:trust:internal-error"));
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ProblemDetail> handleGeneric(final Exception ex) {
        log.error("Unhandled exception in trust module", ex);
        final ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(URI.create("urn:tiibntick:trust:internal-error"));
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
