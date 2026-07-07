package com.yowyob.tiibntick.core.resource.adapter.in.web;

import com.yowyob.tiibntick.core.resource.domain.exception.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Instant;

/**
 * Global exception handler for tnt-resource-core REST adapters.
 * Maps domain exceptions to RFC 7807 Problem Detail responses.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.yowyob.tiibntick.core.resource.adapter.in.web")
public class ResourceExceptionHandler {

    @ExceptionHandler(VehicleNotFoundException.class)
    public Mono<ProblemDetail> handleVehicleNotFound(VehicleNotFoundException ex) {
        log.warn("Vehicle not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:resource:vehicle-not-found"));
        pd.setTitle("Vehicle Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(EquipmentNotFoundException.class)
    public Mono<ProblemDetail> handleEquipmentNotFound(EquipmentNotFoundException ex) {
        log.warn("Equipment not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:resource:equipment-not-found"));
        pd.setTitle("Equipment Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(FreelancerVehicleNotFoundException.class)
    public Mono<ProblemDetail> handleFreelancerVehicleNotFound(FreelancerVehicleNotFoundException ex) {
        log.warn("Freelancer vehicle not found: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:resource:freelancer-vehicle-not-found"));
        pd.setTitle("Freelancer Vehicle Not Found");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(NoAvailableVehicleException.class)
    public Mono<ProblemDetail> handleNoAvailableVehicle(NoAvailableVehicleException ex) {
        log.warn("No available vehicle: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:resource:no-available-vehicle"));
        pd.setTitle("No Available Vehicle");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(VehicleStatusTransitionException.class)
    public Mono<ProblemDetail> handleInvalidTransition(VehicleStatusTransitionException ex) {
        log.warn("Invalid vehicle status transition: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:resource:invalid-status-transition"));
        pd.setTitle("Invalid Vehicle Status Transition");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(VehicleCapacityExceededException.class)
    public Mono<ProblemDetail> handleCapacityExceeded(VehicleCapacityExceededException ex) {
        log.warn("Vehicle capacity exceeded: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422), ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:resource:capacity-exceeded"));
        pd.setTitle("Vehicle Capacity Exceeded");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(FreelancerFleetCapacityExceededException.class)
    public Mono<ProblemDetail> handleFleetCapacityExceeded(FreelancerFleetCapacityExceededException ex) {
        log.warn("Freelancer fleet capacity exceeded: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.valueOf(422), ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:resource:fleet-capacity-exceeded"));
        pd.setTitle("Fleet Capacity Exceeded");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Resource validation error: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setType(URI.create("urn:tiibntick:resource:conflict"));
        pd.setTitle("Resource Conflict");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ProblemDetail> handleResponseStatus(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        pd.setType(URI.create("urn:tiibntick:resource:request-error"));
        pd.setTitle("Request Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }

    @ExceptionHandler(Exception.class)
    public Mono<ProblemDetail> handleGeneric(Exception ex) {
        log.error("Unhandled exception in resource module", ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        pd.setType(URI.create("urn:tiibntick:resource:internal-error"));
        pd.setTitle("Internal Server Error");
        pd.setProperty("timestamp", Instant.now());
        return Mono.just(pd);
    }
}
