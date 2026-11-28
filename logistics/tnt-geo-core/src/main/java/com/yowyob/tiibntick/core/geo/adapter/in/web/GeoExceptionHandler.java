package com.yowyob.tiibntick.core.geo.adapter.in.web;

import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.geo.adapter.in.web")
public class GeoExceptionHandler {

    @ExceptionHandler(GeoNotFoundException.class)
    public Mono<ProblemDetail> handleNotFound(GeoNotFoundException ex) {
        log.warn("Geo resource not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:geo:not-found"));
        pd.setTitle("Geo Resource Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    /** A change refused by the hub's status, e.g. revoking an already revoked hub (D-129): 409. */
    @ExceptionHandler(RelayHubStateException.class)
    public Mono<ProblemDetail> handleHubState(RelayHubStateException ex) {
        log.warn("Relay hub operation refused: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:geo:hub-state"));
        pd.setTitle("Relay Hub State Conflict");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
