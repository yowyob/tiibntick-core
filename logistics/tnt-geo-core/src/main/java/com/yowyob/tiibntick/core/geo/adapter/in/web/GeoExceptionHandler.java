package com.yowyob.tiibntick.core.geo.adapter.in.web;

import com.yowyob.tiibntick.core.geo.domain.exception.GeoNotFoundException;
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
}
