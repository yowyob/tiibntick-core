package com.yowyob.tiibntick.core.actor.adapter.in.web;

import com.yowyob.tiibntick.core.actor.domain.exception.ActorNotAvailableException;
import com.yowyob.tiibntick.core.actor.domain.exception.DelivererNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.exception.FreelancerNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.actor.adapter.in.web")
public class ActorExceptionHandler {

    @ExceptionHandler(ActorNotAvailableException.class)
    public Mono<ProblemDetail> handleActorNotAvailable(ActorNotAvailableException ex) {
        log.warn("Actor not available: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:actor:not-available"));
        pd.setTitle("Actor Not Available");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(DelivererNotFoundException.class)
    public Mono<ProblemDetail> handleDelivererNotFound(DelivererNotFoundException ex) {
        log.warn("Deliverer not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:actor:deliverer-not-found"));
        pd.setTitle("Deliverer Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(FreelancerNotFoundException.class)
    public Mono<ProblemDetail> handleFreelancerNotFound(FreelancerNotFoundException ex) {
        log.warn("Freelancer not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:actor:freelancer-not-found"));
        pd.setTitle("Freelancer Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
