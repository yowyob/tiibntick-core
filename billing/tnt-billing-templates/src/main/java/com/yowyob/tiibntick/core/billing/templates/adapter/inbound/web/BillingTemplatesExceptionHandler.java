package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web;

import com.yowyob.tiibntick.core.billing.templates.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler for the billing templates REST API.
 *
 * <p>Maps domain exceptions to RFC 9457 Problem Detail responses, providing
 * consistent and informative error payloads to API consumers.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web")
public class BillingTemplatesExceptionHandler {

    private static final String BASE_URI = "https://tiibntick.yowyob.com/errors/billing-templates/";

    /**
     * Handles template not found errors → HTTP 404.
     */
    @ExceptionHandler(TemplateNotFoundException.class)
    public Mono<ProblemDetail> handleTemplateNotFound(TemplateNotFoundException ex) {
        log.warn("Template not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setType(URI.create(BASE_URI + "template-not-found"));
        problem.setTitle("Template Not Found");
        problem.setProperty("timestamp", Instant.now());
        return Mono.just(problem);
    }

    /**
     * Handles template not applicable errors → HTTP 403.
     */
    @ExceptionHandler(TemplateNotApplicableException.class)
    public Mono<ProblemDetail> handleTemplateNotApplicable(TemplateNotApplicableException ex) {
        log.warn("Template not applicable: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create(BASE_URI + "template-not-applicable"));
        problem.setTitle("Template Not Applicable");
        problem.setProperty("timestamp", Instant.now());
        return Mono.just(problem);
    }

    /**
     * Handles template inactive errors → HTTP 410 Gone.
     */
    @ExceptionHandler(TemplateInactiveException.class)
    public Mono<ProblemDetail> handleTemplateInactive(TemplateInactiveException ex) {
        log.warn("Template is inactive: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.GONE, ex.getMessage());
        problem.setType(URI.create(BASE_URI + "template-inactive"));
        problem.setTitle("Template Inactive");
        problem.setProperty("timestamp", Instant.now());
        return Mono.just(problem);
    }

    /**
     * Handles template parameter validation errors → HTTP 400 with error list.
     */
    @ExceptionHandler(TemplateParameterValidationException.class)
    public Mono<ProblemDetail> handleParameterValidation(TemplateParameterValidationException ex) {
        log.warn("Template parameter validation failed: {}", ex.getValidationErrors());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(BASE_URI + "parameter-validation-error"));
        problem.setTitle("Template Parameter Validation Failed");
        problem.setProperty("validationErrors", ex.getValidationErrors());
        problem.setProperty("timestamp", Instant.now());
        return Mono.just(problem);
    }

    /**
     * Handles duplicate name and other illegal argument errors → HTTP 409 Conflict.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setType(URI.create(BASE_URI + "conflict"));
        problem.setTitle("Conflict");
        problem.setProperty("timestamp", Instant.now());
        return Mono.just(problem);
    }

    /**
     * Handles ownership security violations → HTTP 403 Forbidden.
     */
    @ExceptionHandler(SecurityException.class)
    public Mono<ProblemDetail> handleSecurity(SecurityException ex) {
        log.warn("Security violation: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        problem.setType(URI.create(BASE_URI + "forbidden"));
        problem.setTitle("Access Denied");
        problem.setProperty("timestamp", Instant.now());
        return Mono.just(problem);
    }
}
