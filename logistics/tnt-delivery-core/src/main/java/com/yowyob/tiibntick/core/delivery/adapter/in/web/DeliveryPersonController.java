package com.yowyob.tiibntick.core.delivery.adapter.in.web;

import com.yowyob.tiibntick.core.delivery.adapter.in.web.request.RegisterDeliveryPersonRequest;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.request.UpdateLocationRequest;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.response.DeliveryPersonResponse;
import com.yowyob.tiibntick.core.delivery.adapter.in.web.response.DeliveryPersonResponseMapper;
import com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryPersonRepository;
import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for delivery person registration and management.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Delivery Persons", description = "Register and manage delivery persons (livreurs)")
@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/delivery-persons")
@RequiredArgsConstructor
public class DeliveryPersonController {

    private final DeliveryPersonRepository deliveryPersonRepository;

    @Operation(summary = "Register a new delivery person")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<DeliveryPersonResponse> register(
            @PathVariable UUID tenantId,
            @Valid @RequestBody RegisterDeliveryPersonRequest req) {

        DeliveryPerson dp = DeliveryPerson.register(
                tenantId, req.actorId(), req.logisticsType(), req.logisticsClass(),
                req.tankCapacity(), req.grossFloor(), req.totalSeatNumber(),
                req.color(), req.commercialRegisterNumber());

        return deliveryPersonRepository.save(dp)
                .map(DeliveryPersonResponseMapper::toResponse);
    }

    @Operation(summary = "Get delivery person by ID")
    @GetMapping("/{deliveryPersonId}")
    public Mono<DeliveryPersonResponse> getById(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryPersonId) {
        return deliveryPersonRepository.findById(tenantId, deliveryPersonId)
                .switchIfEmpty(Mono.error(new DeliveryDomainException(
                    "Delivery person not found: " + deliveryPersonId)))
                .map(DeliveryPersonResponseMapper::toResponse);
    }

    @Operation(summary = "Approve a delivery person")
    @PostMapping("/{deliveryPersonId}/approve")
    public Mono<DeliveryPersonResponse> approve(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryPersonId) {
        return deliveryPersonRepository.findById(tenantId, deliveryPersonId)
                .switchIfEmpty(Mono.error(new DeliveryDomainException(
                    "Delivery person not found: " + deliveryPersonId)))
                .flatMap(dp -> {
                    dp.approve();
                    return deliveryPersonRepository.save(dp);
                })
                .map(DeliveryPersonResponseMapper::toResponse);
    }

    @Operation(summary = "Suspend a delivery person")
    @PostMapping("/{deliveryPersonId}/suspend")
    public Mono<DeliveryPersonResponse> suspend(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryPersonId) {
        return deliveryPersonRepository.findById(tenantId, deliveryPersonId)
                .switchIfEmpty(Mono.error(new DeliveryDomainException(
                    "Delivery person not found: " + deliveryPersonId)))
                .flatMap(dp -> {
                    dp.suspend();
                    return deliveryPersonRepository.save(dp);
                })
                .map(DeliveryPersonResponseMapper::toResponse);
    }

    @Operation(summary = "Update delivery person real-time location")
    @PostMapping("/{deliveryPersonId}/location")
    public Mono<DeliveryPersonResponse> updateLocation(
            @PathVariable UUID tenantId,
            @PathVariable UUID deliveryPersonId,
            @Valid @RequestBody UpdateLocationRequest req) {
        return deliveryPersonRepository.findById(tenantId, deliveryPersonId)
                .switchIfEmpty(Mono.error(new DeliveryDomainException(
                    "Delivery person not found: " + deliveryPersonId)))
                .flatMap(dp -> {
                    dp.updateLocation(req.toCoordinates());
                    return deliveryPersonRepository.save(dp);
                })
                .map(DeliveryPersonResponseMapper::toResponse);
    }
}
