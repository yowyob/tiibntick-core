package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.RegisterTntClientProfileUseCase;
import com.yowyob.tiibntick.core.tp.application.port.in.command.GeneratePhoneAliasCommand;
import com.yowyob.tiibntick.core.tp.application.port.in.command.RegisterTntClientProfileCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.KernelThirdPartyPort;
import com.yowyob.tiibntick.core.tp.application.port.out.PhoneAliasPort;
import com.yowyob.tiibntick.core.tp.application.port.out.TntClientProfileRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.exception.TntThirdPartyNotFoundException;
import com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for {@link TntClientProfile} lifecycle management.
 *
 * <p><strong> — tnt-roles-core integration:</strong> Key public methods are
 * annotated with {@code @RequirePermission} for declarative RBAC enforcement
 * via the {@link com.yowyob.tiibntick.core.roles.domain.model.TntPermission} catalog.
 *
 * <p>Orchestration steps for {@link #register}:
 * <ol>
 *   <li>Validates the Kernel ThirdParty reference via {@link KernelThirdPartyPort}.</li>
 *   <li>Checks that no profile already exists for the (tenantId, thirdPartyId) pair.</li>
 *   <li>Creates and persists the profile aggregate.</li>
 *   <li>Publishes domain events.</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */
@Service
public class TntClientProfileService implements RegisterTntClientProfileUseCase {

    private final TntClientProfileRepository profileRepository;
    private final TntTpEventPublisher eventPublisher;
    private final PhoneAliasPort phoneAliasPort;
    private final KernelThirdPartyPort kernelThirdPartyPort;

    /**
     * Constructor injection.
     *
     * @param profileRepository   persistence port for TntClientProfile aggregates
     * @param eventPublisher      Kafka event publisher
     * @param phoneAliasPort      phone alias generation port
     * @param kernelThirdPartyPort Kernel TP validation port
     */
    public TntClientProfileService(
            TntClientProfileRepository profileRepository,
            TntTpEventPublisher eventPublisher,
            PhoneAliasPort phoneAliasPort,
            KernelThirdPartyPort kernelThirdPartyPort) {
        this.profileRepository = profileRepository;
        this.eventPublisher = eventPublisher;
        this.phoneAliasPort = phoneAliasPort;
        this.kernelThirdPartyPort = kernelThirdPartyPort;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Validates the Kernel ThirdParty existence and activity before creating
     * the profile. Emits {@code ClientProfileRegisteredEvent} after persistence.
     */
    @Override
    @RequirePermission(resource = "actor", action = "write")
    public Mono<TntClientProfile> register(RegisterTntClientProfileCommand command) {
        // Step 1: validate that the Kernel ThirdParty exists and is active
        return kernelThirdPartyPort.existsAndActive(command.thirdPartyId())
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Kernel ThirdParty not found or inactive: " + command.thirdPartyId()));
                    }
                    // Step 2: prevent duplicate profiles for the same (tenantId, thirdPartyId)
                    return profileRepository.existsByThirdPartyId(
                            command.tenantId(), command.thirdPartyId());
                })
                .flatMap(alreadyExists -> {
                    if (Boolean.TRUE.equals(alreadyExists)) {
                        return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                                "Profile already exists for thirdPartyId=" + command.thirdPartyId()));
                    }
                    // Step 3: create the aggregate
                    TntClientProfile profile = TntClientProfile.create(
                            command.tenantId(),
                            command.thirdPartyId(),
                            command.roles(),
                            command.preferredLocale(),
                            command.preferredCurrency());
                    // Step 4: persist and publish domain events
                    return profileRepository.save(profile)
                            .flatMap(saved -> {
                                var events = saved.collectAndClearEvents();
                                return eventPublisher.publishAll(events).thenReturn(saved);
                            });
                });
    }

    /**
     * Retrieves a client profile by its internal TiiBnTick profile UUID.
     *
     * @param profileId the profile's internal UUID
     * @return a {@link Mono} emitting the profile, or an error if not found
     */
    @RequirePermission(resource = "actor", action = "read")
    public Mono<TntClientProfile> getByProfileId(UUID profileId) {
        return profileRepository.findById(profileId)
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(profileId)));
    }

    /**
     * Retrieves a client profile by its Kernel ThirdParty UUID and tenant.
     *
     * @param tenantId     the multi-tenant key
     * @param thirdPartyId the Kernel ThirdParty UUID
     * @return a {@link Mono} emitting the profile, or an error if not found
     */
    @RequirePermission(resource = "actor", action = "read")
    public Mono<TntClientProfile> getByThirdPartyId(UUID tenantId, UUID thirdPartyId) {
        return profileRepository.findByThirdPartyId(tenantId, thirdPartyId)
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(
                        "thirdPartyId=" + thirdPartyId)));
    }

    /**
     * Generates and assigns a phone alias for relay-point anonymity.
     *
     * @param command the generate phone alias command
     * @return a {@link Mono} emitting the updated profile with masked phone
     */
    @RequirePermission(resource = "actor", action = "write")
    public Mono<TntClientProfile> generatePhoneAlias(GeneratePhoneAliasCommand command) {
        return profileRepository.findByThirdPartyId(command.tenantId(), command.thirdPartyId())
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(command.thirdPartyId())))
                .flatMap(profile -> phoneAliasPort
                        .generateAlias(command.tenantId(), command.thirdPartyId())
                        .flatMap(alias -> {
                            TntClientProfile updated = profile.assignPhoneAlias(alias);
                            return profileRepository.save(updated);
                        }));
    }

    /**
     * Removes the phone alias (reveals the real phone number).
     *
     * @param tenantId     the multi-tenant key
     * @param thirdPartyId the Kernel ThirdParty UUID
     * @return a {@link Mono} emitting the updated profile with unmasked phone
     */
    @RequirePermission(resource = "actor", action = "write")
    public Mono<TntClientProfile> removePhoneAlias(UUID tenantId, UUID thirdPartyId) {
        return profileRepository.findByThirdPartyId(tenantId, thirdPartyId)
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(thirdPartyId)))
                .flatMap(profile -> {
                    TntClientProfile updated = profile.removePhoneAlias();
                    return profileRepository.save(updated)
                            .flatMap(saved -> phoneAliasPort.revokeAlias(tenantId, thirdPartyId)
                                    .thenReturn(saved));
                });
    }

    /**
     * Updates the loyalty tier on the profile when the points balance changes.
     *
     * @param tenantId     the multi-tenant key
     * @param thirdPartyId the Kernel ThirdParty UUID
     * @param points       the current lifetime loyalty points total
     * @return a {@link Mono} emitting the updated profile (or empty if not found)
     */
    public Mono<TntClientProfile> updateLoyaltyTierOnProfile(UUID tenantId, UUID thirdPartyId, int points) {
        return profileRepository.findByThirdPartyId(tenantId, thirdPartyId)
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(thirdPartyId)))
                .flatMap(profile -> profileRepository.save(profile.updateLoyaltyTier(points)));
    }

    /**
     * Increments the successful deliveries counter on the profile.
     *
     * @param tenantId     the multi-tenant key
     * @param thirdPartyId the Kernel ThirdParty UUID
     * @return a {@link Mono} emitting the updated profile
     */
    public Mono<TntClientProfile> incrementDeliveries(UUID tenantId, UUID thirdPartyId) {
        return profileRepository.findByThirdPartyId(tenantId, thirdPartyId)
                .switchIfEmpty(Mono.error(new TntThirdPartyNotFoundException(thirdPartyId)))
                .flatMap(profile -> profileRepository.save(profile.incrementDeliveries()));
    }
}
