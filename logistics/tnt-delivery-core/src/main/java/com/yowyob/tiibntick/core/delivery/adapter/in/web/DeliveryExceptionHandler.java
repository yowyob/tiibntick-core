package com.yowyob.tiibntick.core.delivery.adapter.in.web;

import com.yowyob.tiibntick.core.delivery.domain.exception.AnnouncementNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryNotFoundException;
import com.yowyob.tiibntick.core.delivery.domain.exception.InvalidDeliveryStateTransitionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler for tnt-delivery-core REST adapters.
 * Maps domain exceptions to RFC 7807 Problem Detail responses.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.delivery.adapter.in.web")
public class DeliveryExceptionHandler {

    @ExceptionHandler(DeliveryNotFoundException.class)
    public Mono<ProblemDetail> handleDeliveryNotFound(DeliveryNotFoundException ex) {
        log.warn("Delivery not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:delivery:not-found"));
        pd.setTitle("Delivery Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(AnnouncementNotFoundException.class)
    public Mono<ProblemDetail> handleAnnouncementNotFound(AnnouncementNotFoundException ex) {
        log.warn("Announcement not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:announcement:not-found"));
        pd.setTitle("Announcement Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(InvalidDeliveryStateTransitionException.class)
    public Mono<ProblemDetail> handleInvalidTransition(InvalidDeliveryStateTransitionException ex) {
        log.warn("Invalid delivery state transition: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:delivery:invalid-transition"));
        pd.setTitle("Invalid Delivery State Transition");
        pd.setProperty("from", ex.getFrom().name());
        pd.setProperty("to", ex.getTo().name());
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public Mono<ProblemDetail> handleDuplicateKey(DuplicateKeyException ex) {
        log.warn("Duplicate key in delivery module: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "A record with the same unique identifier already exists.");
        pd.setType(URI.create("urn:tiibntick:delivery:duplicate-key"));
        pd.setTitle("Duplicate Key");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(DeliveryDomainException.class)
    public Mono<ProblemDetail> handleDomainException(DeliveryDomainException ex) {
        log.warn("Delivery domain error: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:delivery:domain-error"));
        pd.setTitle("Delivery Domain Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ProblemDetail> handleValidationError(WebExchangeBindException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setType(URI.create("urn:tiibntick:delivery:validation-error"));
        pd.setTitle("Validation Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Access denied in delivery module: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Access denied");
        pd.setType(URI.create("urn:tiibntick:delivery:access-denied"));
        pd.setTitle("Forbidden");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.resolve(ex.getStatusCode().value()), ex.getReason());
        pd.setType(URI.create("urn:tiibntick:delivery:error"));
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception in delivery module", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(URI.create("urn:tiibntick:delivery:internal-error"));
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
