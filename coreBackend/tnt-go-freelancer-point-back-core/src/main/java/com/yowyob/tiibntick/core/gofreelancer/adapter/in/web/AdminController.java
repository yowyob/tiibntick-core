package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tnt-go-freelancer/dashboard")
@RequiredArgsConstructor
public class AdminController {

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<ResponseEntity<Object>> getDashboardStats() {
        log.info("Direct Admin get stats");
        return Mono.just(ResponseEntity.ok().build());
    }
}
