package com.yowyob.tiibntick.core.tp.adapter.in.web;

import com.yowyob.tiibntick.core.tp.domain.exception.TntThirdPartyNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.tp.adapter.in.web")
public class TpExceptionHandler {

    @ExceptionHandler(TntThirdPartyNotFoundException.class)
    public Mono<ProblemDetail> handleNotFound(TntThirdPartyNotFoundException ex) {
        log.warn("ThirdParty/profile not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:tp:third-party-not-found"));
        pd.setTitle("Third Party Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
