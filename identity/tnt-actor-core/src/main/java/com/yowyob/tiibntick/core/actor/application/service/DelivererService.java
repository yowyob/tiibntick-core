package com.yowyob.tiibntick.core.actor.application.service;

import com.yowyob.tiibntick.core.actor.application.command.CreateDelivererProfileCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ICreateDelivererProfileUseCase;
import com.yowyob.tiibntick.core.actor.application.port.in.IFindDelivererUseCase;
import com.yowyob.tiibntick.core.actor.application.port.out.IActorEventPublisher;
import com.yowyob.tiibntick.core.actor.application.port.out.IDelivererRepository;
import com.yowyob.tiibntick.core.actor.application.port.out.IKernelActorPort;
import com.yowyob.tiibntick.core.actor.domain.event.ActorStatusChangedEvent;
import com.yowyob.tiibntick.core.actor.domain.exception.DelivererNotFoundException;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for deliverer profile lifecycle management.
 *
 * <p> — Added {@code @RequirePermission} guards from tnt-roles-core on write operations.
 *
 * @author MANFOUO Braun
 */
@Service
public class DelivererService implements ICreateDelivererProfileUseCase, IFindDelivererUseCase {

    private static final Logger log = LoggerFactory.getLogger(DelivererService.class);

    private final IDelivererRepository delivererRepository;
    private final IActorEventPublisher eventPublisher;
    private final IKernelActorPort kernelActorPort;

    public DelivererService(IDelivererRepository delivererRepository,
                            IActorEventPublisher eventPublisher,
                            IKernelActorPort kernelActorPort) {
        this.delivererRepository = delivererRepository;
        this.eventPublisher = eventPublisher;
        this.kernelActorPort = kernelActorPort;
    }

    /**
     * Creates (or retrieves if already exists) a deliverer profile for the given command.
     * Requires {@code actor:write} permission.
     *
     * @param command the creation command with tenant, actorId, agency, branch and capacity info
     * @return the created or existing deliverer profile
     */
    @Override
    @RequirePermission(resource = "actor", action = "write")
    @Transactional
    public Mono<DelivererProfile> createDelivererProfile(CreateDelivererProfileCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return delivererRepository.existsByActorId(command.tenantId(), command.actorId())
                .flatMap(exists -> {
                    if (exists) {
                        return delivererRepository.findByActorId(command.tenantId(), command.actorId());
                    }
                    return kernelActorPort.exists(command.actorId())
                            .doOnNext(found -> {
                                if (!found) {
                                    log.warn("Kernel actor {} not found or unreachable — " +
                                            "creating deliverer profile without Kernel validation", command.actorId());
                                }
                            })
                            .then(Mono.defer(() -> {
                                DelivererProfile profile = DelivererProfile.create(
                                        command.tenantId(),
                                        command.actorId(),
                                        command.agencyId(),
                                        command.branchId(),
                                        command.capacityKg(),
                                        command.delivererType());
                                return delivererRepository.save(profile)
                                        .flatMap(saved -> eventPublisher.publishActorStatusChanged(
                                                ActorStatusChangedEvent.of(saved.actorId(), saved.tenantId(),
                                                        null, saved.actorStatus().name(), "profile_created"))
                                                .thenReturn(saved));
                            }));
                });
    }

    @Override
    @RequirePermission(resource = "actor", action = "read")
    public Mono<DelivererProfile> findByActorId(UUID tenantId, UUID actorId) {
        return delivererRepository.findByActorId(tenantId, actorId)
                .switchIfEmpty(Mono.error(new DelivererNotFoundException(tenantId, actorId)));
    }

    @Override
    @RequirePermission(resource = "actor", action = "read")
    public Flux<DelivererProfile> findByAgency(UUID tenantId, UUID agencyId) {
        return delivererRepository.findByAgencyId(tenantId, agencyId);
    }

    @Override
    @RequirePermission(resource = "actor", action = "read")
    public Flux<DelivererProfile> findByBranch(UUID tenantId, UUID branchId) {
        return delivererRepository.findByBranchId(tenantId, branchId);
    }

    @Override
    @RequirePermission(resource = "actor", action = "read")
    public Flux<DelivererProfile> findAvailableInBranch(UUID tenantId, UUID branchId) {
        return delivererRepository.findAvailableByBranchId(tenantId, branchId);
    }
}
