package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.UserStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.GofpUserUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * REST controller for GofpUser management.
 *
 * Base path: /api/v1/gofp/users
 *
 * @author François-Charles ATANGA
 */
@RestController
@RequestMapping("/api/v1/gofp/users")
@RequiredArgsConstructor
public class GofpUserController {

    private final GofpUserUseCase userUseCase;

    @PostMapping
    public Mono<ResponseEntity<GofpUser>> createOrUpdate(@RequestBody GofpUser user) {
        return userUseCase.createOrUpdate(user)
                .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<GofpUser>> getById(@PathVariable UUID id) {
        return userUseCase.findById(id)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-core-user/{coreUserId}")
    public Mono<ResponseEntity<GofpUser>> getByCoreUserId(@PathVariable UUID coreUserId) {
        return userUseCase.findByCoreUserId(coreUserId)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping("/by-email/{email}")
    public Mono<ResponseEntity<GofpUser>> getByEmail(@PathVariable String email) {
        return userUseCase.findByEmail(email)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @GetMapping
    public Flux<GofpUser> getAll() {
        return userUseCase.findAll();
    }

    @PatchMapping("/{id}/status")
    public Mono<ResponseEntity<GofpUser>> updateStatus(
            @PathVariable UUID id,
            @RequestParam UserStatus status) {
        return userUseCase.updateStatus(id, status)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(@PathVariable UUID id) {
        return userUseCase.delete(id)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()));
    }
}
