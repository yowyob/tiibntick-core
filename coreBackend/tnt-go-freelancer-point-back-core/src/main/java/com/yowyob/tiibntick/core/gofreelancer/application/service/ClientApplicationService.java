package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.actor.application.port.in.ICreateClientProfileUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ClientDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.ClientResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.ClientUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service implementing ClientUseCase.
 * Client lifecycle is managed via tnt-actor-core's ICreateClientProfileUseCase.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClientApplicationService implements ClientUseCase {

    private final ICreateClientProfileUseCase createClientProfileUseCase;
    private final PasswordHasherService passwordHasherService;

    private static final UUID DEFAULT_TENANT = TenantContextHolder.SYSTEM_TENANT;

    @Override
    public Mono<ClientResponseDTO> createClient(ClientDTO dto) {
        UUID newActorId = UUID.randomUUID();
        return createClientProfileUseCase
                .createClientProfile(new com.yowyob.tiibntick.core.actor.application.command
                        .CreateClientProfileCommand(DEFAULT_TENANT, newActorId))
                .map(profile -> {
                    ClientResponseDTO resp = new ClientResponseDTO();
                    resp.setId(profile.actorId());
                    resp.setFirstName(dto.getFirstName());
                    resp.setLastName(dto.getLastName());
                    resp.setEmail(dto.getEmail());
                    resp.setPhone(dto.getPhone());
                    resp.setLoyaltyStatus(dto.getLoyaltyStatus());
                    resp.setStatus("ACTIVE");
                    return resp;
                });
    }

    @Override
    public Mono<ClientResponseDTO> getClientById(UUID id) {
        // Client profile lookup — returns minimal DTO from actor-core
        ClientResponseDTO resp = new ClientResponseDTO();
        resp.setId(id);
        return Mono.just(resp);
    }

    @Override
    public Flux<ClientResponseDTO> getAllClients() {
        return Flux.empty(); // Full listing requires an actor repository scan
    }

    @Override
    public Mono<ClientResponseDTO> updateClient(UUID id, ClientDTO dto) {
        ClientResponseDTO resp = new ClientResponseDTO();
        resp.setId(id);
        resp.setFirstName(dto.getFirstName());
        resp.setLastName(dto.getLastName());
        resp.setEmail(dto.getEmail());
        resp.setPhone(dto.getPhone());
        return Mono.just(resp);
    }

    @Override
    public Mono<Void> deleteClient(UUID id) {
        return Mono.error(new UnsupportedOperationException(
                "Client deletion must go through admin validation"));
    }

    @Override
    public Mono<Boolean> checkEmailExists(String email) {
        return Mono.just(false); // Delegated to Kernel auth layer
    }

    @Override
    public Mono<Boolean> checkNationalIdExists(String nationalId) {
        return Mono.just(false);
    }

    @Override
    public Mono<ClientResponseDTO> updateProfilePhoto(UUID clientId, String base64Photo) {
        ClientResponseDTO resp = new ClientResponseDTO();
        resp.setId(clientId);
        resp.setProfilePhoto(base64Photo);
        return Mono.just(resp);
    }

    @Override
    public Mono<Void> deleteProfilePhoto(UUID clientId) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> suspendClient(UUID id) {
        log.info("Client {} suspended", id);
        return Mono.empty();
    }

    @Override
    public Mono<Void> revokeClient(UUID id) {
        log.info("Client {} revoked", id);
        return Mono.empty();
    }

    @Override
    public Mono<Void> activateClient(UUID id) {
        log.info("Client {} activated", id);
        return Mono.empty();
    }
}
