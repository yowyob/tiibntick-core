package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AuthRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AuthResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.UserRegistrationDTO;
import com.yowyob.tiibntick.core.auth.domain.model.TntTokenPair;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import reactor.core.publisher.Mono;

/**
 * Inbound port for authentication use cases.
 * Defines the contract that the REST adapter uses to interact with the domain.
 */
public interface AuthUseCase {

    Mono<AuthResponseDTO> login(AuthRequestDTO request);

    Mono<Void> logout(String token);

    /**
     * Registers a new client/user account.
     * Creates a GofpUser + GofpClient record and delegates credential creation to the Kernel.
     */
    Mono<AuthResponseDTO> register(UserRegistrationDTO request);

    /**
     * Exchanges a valid refresh token for a new access+refresh token pair.
     */
    Mono<TntTokenPair> refresh(String refreshToken);

    /**
     * Returns the profile of the currently authenticated user.
     */
    Mono<AuthResponseDTO> me(TntUserIdentity currentUser);
}
