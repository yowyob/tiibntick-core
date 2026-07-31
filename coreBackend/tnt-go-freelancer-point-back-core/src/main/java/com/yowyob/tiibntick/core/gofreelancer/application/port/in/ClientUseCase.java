package com.yowyob.tiibntick.core.gofreelancer.application.port.in;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ClientDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ClientResponseDTO;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Inbound port for client management use cases.
 */
public interface ClientUseCase {

    Mono<ClientResponseDTO> createClient(ClientDTO clientDTO);
    Mono<ClientResponseDTO> getClientById(UUID id);
    Flux<ClientResponseDTO> getAllClients();
    Mono<ClientResponseDTO> updateClient(UUID id, ClientDTO clientDTO);
    Mono<Void> deleteClient(UUID id);
    Mono<Boolean> checkEmailExists(String email);
    Mono<Boolean> checkNationalIdExists(String nationalId);
    Mono<ClientResponseDTO> updateProfilePhoto(UUID clientId, String base64Photo);
    Mono<Void> deleteProfilePhoto(UUID clientId);

    // ── Admin operations ──────────────────────────────────────────────
    Mono<Void> suspendClient(UUID id);
    Mono<Void> revokeClient(UUID id);
    Mono<Void> activateClient(UUID id);
}
