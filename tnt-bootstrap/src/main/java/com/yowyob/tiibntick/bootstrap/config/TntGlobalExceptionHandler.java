package com.yowyob.tiibntick.bootstrap.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * Global fallback exception handler for all TiiBnTick modules.
 * Catches common domain exceptions not handled by module-specific advisors.
 * Runs at lowest priority ({@code @Order(Integer.MAX_VALUE)}) so module-specific
 * advisors take precedence.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@RestControllerAdvice
@Order(Integer.MAX_VALUE)
public class TntGlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Domain validation error: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:validation-error"));
        pd.setTitle("Invalid Request");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(IllegalStateException.class)
    public Mono<ProblemDetail> handleIllegalState(IllegalStateException ex) {
        log.warn("Domain state conflict: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:state-conflict"));
        pd.setTitle("State Conflict");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
