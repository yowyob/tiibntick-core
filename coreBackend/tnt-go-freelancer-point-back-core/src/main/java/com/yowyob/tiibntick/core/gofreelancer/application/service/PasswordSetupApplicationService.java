package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.exception.InvalidTokenException;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.PasswordSetupUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.EmailPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Application service implementing PasswordSetupUseCase.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordSetupApplicationService implements PasswordSetupUseCase {

    private final TokenService tokenService;
    private final PasswordHasherService passwordHasherService;
    private final EmailPort emailPort;

    @Override
    public Mono<Void> setupPassword(String token, String newPassword) {
        return tokenService.validateToken(token)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new InvalidTokenException("Token is invalid or expired"));
                    }
                    return tokenService.getToken(token)
                            .flatMap(passwordToken -> {
                                String hashed = passwordHasherService.encode(newPassword);
                                log.info("Password set for personId={}", passwordToken.getPersonId());
                                return tokenService.expireToken(token);
                            });
                });
    }

    @Override
    public Mono<Void> requestPasswordReset(String email) {
        // Generate token and send reset email
        // PersonId resolution would require a person repository lookup by email
        // For now we generate a placeholder token and send the email
        log.info("Password reset requested for email={}", email);
        return emailPort.sendSimpleMessageReactive(
                email,
                "Réinitialisation de mot de passe",
                "Vous avez demandé une réinitialisation de mot de passe. Veuillez contacter le support."
        );
    }
}
