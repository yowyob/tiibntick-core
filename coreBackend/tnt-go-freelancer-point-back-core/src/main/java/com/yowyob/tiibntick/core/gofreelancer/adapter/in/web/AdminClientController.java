package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tnt-go-freelancer/clients")
@RequiredArgsConstructor
public class AdminClientController {

    @PutMapping("/{id}/suspend")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> suspendClient(@PathVariable UUID id, @RequestParam(required = false) String loginUrl) {
        log.info("Direct Admin suspend client {}", id);
        return Mono.just(ResponseEntity.ok().build());
    }

    @PutMapping("/{id}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> revokeClient(@PathVariable UUID id, @RequestParam(required = false) String loginUrl) {
        log.info("Direct Admin revoke client {}", id);
        return Mono.just(ResponseEntity.ok().build());
    }

    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Void>> activateClient(@PathVariable UUID id, @RequestParam(required = false) String loginUrl) {
        log.info("Direct Admin activate client {}", id);
        return Mono.just(ResponseEntity.ok().build());
    }
}
