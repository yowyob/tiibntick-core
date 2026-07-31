package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.PasswordToken;
import reactor.core.publisher.Mono;

/**
 * Outbound port for password token persistence operations.
 */
public interface PasswordTokenRepository {

    Mono<PasswordToken> save(PasswordToken token);

    Mono<PasswordToken> findByToken(String token);
}
