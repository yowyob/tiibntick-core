package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.gofreelancer.application.port.in.PasswordSetupUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.SetPasswordRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Inbound REST adapter for password setup.
 * Delegates to the PasswordSetupUseCase inbound port.
 *
 * @author Kengfack Lagrange
 * @date 19/12/2025 — refactored 08/07/2026
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class PasswordSetupController {

    private final PasswordSetupUseCase passwordSetupUseCase;

    @PostMapping("/setup-password")
    public Mono<ResponseEntity<Void>> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        return passwordSetupUseCase.setupPassword(request.getToken(), request.getNewPassword())
                .then(Mono.just(ResponseEntity.ok().<Void>build()));
    }

    @PostMapping("/request-password-reset")
    public Mono<ResponseEntity<Void>> requestPasswordReset(@RequestParam String email) {
        return passwordSetupUseCase.requestPasswordReset(email)
                .then(Mono.just(ResponseEntity.accepted().<Void>build()));
    }
}
