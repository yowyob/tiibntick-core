package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/tnt-go-freelancer/dashboard")
@RequiredArgsConstructor
@RequirePermission(resource = "gofp-admin", action = "read")
public class AdminController {

    @GetMapping("/stats")
    public Mono<ResponseEntity<Object>> getDashboardStats() {
        log.info("Direct Admin get stats");
        return Mono.just(ResponseEntity.ok().build());
    }
}
