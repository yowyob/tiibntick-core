package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest;

import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeAccessDeniedException;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeNotFoundException;
import com.yowyob.tiibntick.core.dispute.domain.exception.DisputeStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler for tnt-dispute-core REST controllers.
 *
 * <p>Maps domain exceptions to RFC 7807 Problem Detail responses,
 * giving clients consistent, structured error payloads.
 *
 * @author MANFOUO Braun
 */
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.dispute.infrastructure.adapter.in.rest")
public class DisputeExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(DisputeExceptionHandler.class);

    @ExceptionHandler(DisputeNotFoundException.class)
    public Mono<ProblemDetail> handleNotFound(DisputeNotFoundException ex) {
        log.warn("Dispute not found: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        detail.setType(URI.create("https://tiibntick.yowyob.com/errors/dispute-not-found"));
        detail.setTitle("Dispute Not Found");
        detail.setProperty("timestamp", Instant.now());
        return Mono.just(detail);
    }

    @ExceptionHandler(DisputeStateException.class)
    public Mono<ProblemDetail> handleStateException(DisputeStateException ex) {
        log.warn("Invalid dispute state transition: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        detail.setType(URI.create("https://tiibntick.yowyob.com/errors/invalid-dispute-state"));
        detail.setTitle("Invalid Dispute State");
        detail.setProperty("timestamp", Instant.now());
        return Mono.just(detail);
    }

    @ExceptionHandler(DisputeAccessDeniedException.class)
    public Mono<ProblemDetail> handleAccessDenied(DisputeAccessDeniedException ex) {
        log.warn("Dispute access denied: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        detail.setType(URI.create("https://tiibntick.yowyob.com/errors/dispute-access-denied"));
        detail.setTitle("Dispute Access Denied");
        detail.setProperty("timestamp", Instant.now());
        return Mono.just(detail);
    }

    @ExceptionHandler(NullPointerException.class)
    public Mono<ProblemDetail> handleNullPointer(NullPointerException ex) {
        log.warn("Missing required field in dispute request: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Missing required field: " + (ex.getMessage() != null ? ex.getMessage() : "a required field is null"));
        detail.setType(URI.create("https://tiibntick.yowyob.com/errors/bad-request"));
        detail.setTitle("Bad Request");
        detail.setProperty("timestamp", Instant.now());
        return Mono.just(detail);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ProblemDetail> handleBadRequest(IllegalArgumentException ex) {
        log.warn("Bad request: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        detail.setType(URI.create("https://tiibntick.yowyob.com/errors/bad-request"));
        detail.setTitle("Bad Request");
        detail.setProperty("timestamp", Instant.now());
        return Mono.just(detail);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation in dispute: {}", ex.getMessage());
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A dispute already exists for this package/mission.");
        detail.setType(URI.create("https://tiibntick.yowyob.com/errors/dispute-duplicate"));
        detail.setTitle("Duplicate Dispute");
        detail.setProperty("timestamp", Instant.now());
        return Mono.just(detail);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.resolve(ex.getStatusCode().value()), ex.getReason());
        detail.setType(URI.create("https://tiibntick.yowyob.com/errors/error"));
        detail.setProperty("timestamp", Instant.now());
        return Mono.just(detail);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Please try again later.");
        detail.setType(URI.create("https://tiibntick.yowyob.com/errors/internal-server-error"));
        detail.setTitle("Internal Server Error");
        detail.setProperty("timestamp", Instant.now());
        return Mono.just(detail);
    }
}
