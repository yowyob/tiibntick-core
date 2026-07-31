package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntTokenPair;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AuthUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AuthRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AuthResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.UserRegistrationDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Inbound REST adapter for authentication.
 *
 * <p>Public endpoints (no JWT required):
 * POST /api/auth/login, POST /api/auth/register, POST /api/auth/refresh
 *
 * <p>Protected endpoints (JWT required):
 * POST /api/auth/logout, GET /api/auth/me
 *
 * @author François-Charles ATANGA
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthUseCase authUseCase;

    @PostMapping("/login")
    public Mono<AuthResponseDTO> login(@RequestBody AuthRequestDTO request) {
        return authUseCase.login(request);
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<AuthResponseDTO>> register(@Valid @RequestBody UserRegistrationDTO request) {
        return authUseCase.register(request)
                .map(resp -> ResponseEntity.status(HttpStatus.CREATED).body(resp))
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.badRequest().build()));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<TntTokenPair>> refresh(@RequestBody Map<String, String> body) {
        String refreshToken = body.get("refreshToken");
        if (refreshToken == null || refreshToken.isBlank()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        return authUseCase.refresh(refreshToken)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@RequestHeader("Authorization") String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return authUseCase.logout(token)
                    .then(Mono.just(ResponseEntity.ok().<Void>build()));
        }
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<AuthResponseDTO>> me(@CurrentUser TntUserIdentity currentUser) {
        return authUseCase.me(currentUser)
                .map(ResponseEntity::ok)
                .onErrorResume(IllegalArgumentException.class,
                        e -> Mono.just(ResponseEntity.notFound().build()));
    }
}
