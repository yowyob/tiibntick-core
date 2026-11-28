package com.yowyob.tiibntick.bootstrap.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
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

    // ── Upstream (Kernel) failures ────────────────────────────────────────────
    // Without these, a Kernel refusal or timeout reached the client as an opaque 500, and anything between
    // the front and the Core (proxy, ingress) was left to invent its own status. The status says WHO failed,
    // the detail says why. 401/403 from the Kernel are never forwarded as such: the front would read a 401 as
    // "token expired" and sign the user out.

    private static final int MAX_UPSTREAM_BODY = 300;

    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ProblemDetail> handleKernelResponse(WebClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body.length() > MAX_UPSTREAM_BODY) {
            body = body.substring(0, MAX_UPSTREAM_BODY) + "…";
        }
        log.warn("Kernel answered HTTP {} to {}: {}", ex.getStatusCode().value(), ex.getRequest() != null
                ? ex.getRequest().getURI().getPath() : "?", body);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The Kernel answered HTTP " + ex.getStatusCode().value() + (body.isBlank() ? "" : ": " + body));
        pd.setType(URI.create("urn:tiibntick:upstream:kernel-error"));
        pd.setTitle("Kernel Error");
        pd.setProperty("upstreamStatus", ex.getStatusCode().value());
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(WebClientRequestException.class)
    public Mono<ProblemDetail> handleKernelUnreachable(WebClientRequestException ex) {
        log.warn("Kernel unreachable: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY,
                "The Kernel could not be reached: " + ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:upstream:kernel-unreachable"));
        pd.setTitle("Kernel Unreachable");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(java.util.concurrent.TimeoutException.class)
    public Mono<ProblemDetail> handleKernelTimeout(java.util.concurrent.TimeoutException ex) {
        log.warn("Kernel call timed out: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT,
                "The Kernel did not answer in time");
        pd.setType(URI.create("urn:tiibntick:upstream:kernel-timeout"));
        pd.setTitle("Kernel Timeout");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class)
    public Mono<ProblemDetail> handleKernelCircuitOpen(
            io.github.resilience4j.circuitbreaker.CallNotPermittedException ex) {
        log.warn("Kernel circuit open: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The Kernel is temporarily unavailable, try again shortly");
        pd.setType(URI.create("urn:tiibntick:upstream:kernel-circuit-open"));
        pd.setTitle("Kernel Temporarily Unavailable");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
