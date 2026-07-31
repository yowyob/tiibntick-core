package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpClient;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.client.ClientStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.LoyaltyStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpClientUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for GofpClient management.
 *
 * Base path: /api/v1/gofp/clients
 *
 * @author François-Charles ATANGA
 */
@RestController
@RequestMapping("/api/v1/gofp/clients")
@RequiredArgsConstructor
public class GofpClientController {

    private final GofpClientUseCase clientUseCase;

    @PostMapping
    public Mono<ResponseEntity<GofpClient>> createOrUpdate(@RequestBody GofpClient client) {
        return clientUseCase.createOrUpdate(client)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<GofpClient>> getById(@PathVariable UUID id) {
        return clientUseCase.findById(id)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-core-client/{coreClientId}")
    public Mono<ResponseEntity<GofpClient>> getByCoreClientId(@PathVariable UUID coreClientId) {
        return clientUseCase.findByCoreClientId(coreClientId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-core-user/{coreUserId}")
    public Mono<ResponseEntity<GofpClient>> getByCoreUserId(@PathVariable UUID coreUserId) {
        return clientUseCase.findByCoreUserId(coreUserId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping
    public Flux<GofpClient> getAll() {
        return clientUseCase.findAll();
    }

    @GetMapping("/by-status/{status}")
    public Flux<GofpClient> getByStatus(@PathVariable ClientStatus status) {
        return clientUseCase.findByStatus(status);
    }

    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<GofpClient>> updateStatus(
            @PathVariable UUID id,
            @RequestParam ClientStatus status) {
        return clientUseCase.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @PatchMapping("/{id}/loyalty")
    public Mono<ResponseEntity<GofpClient>> updateLoyalty(
            @PathVariable UUID id,
            @RequestParam LoyaltyStatus loyaltyStatus) {
        return clientUseCase.updateLoyaltyStatus(id, loyaltyStatus)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    /** Called after a completed order to increment counter and recompute loyalty tier. */
    @PostMapping("/by-core-client/{coreClientId}/record-order")
    public Mono<ResponseEntity<GofpClient>> recordOrder(@PathVariable UUID coreClientId) {
        return clientUseCase.recordOrder(coreClientId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return clientUseCase.delete(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
