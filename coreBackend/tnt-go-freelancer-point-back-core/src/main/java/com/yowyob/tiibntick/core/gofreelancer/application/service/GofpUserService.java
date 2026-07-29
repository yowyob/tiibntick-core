package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.UserStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.in.GofpUserUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.port.out.GofpUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Application service for GofpUser lifecycle management.
 *
 * <p>A GofpUser mirrors the ATANGA users/persons table so the core can
 * operate without round-trips for basic identity data. Records are
 * created/updated via sync calls from ATANGA or from the registration flow.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpUserService implements GofpUserUseCase {

    private final GofpUserRepository userRepository;

    @Override
    public Mono<GofpUser> createOrUpdate(GofpUser user) {
        if (user.getCoreUserId() == null) {
            return Mono.error(new IllegalArgumentException("coreUserId is required"));
        }
        return userRepository.findByCoreUserId(user.getCoreUserId())
                .flatMap(existing -> {
                    // Update existing record — preserve id
                    user.setId(existing.getId());
                    user.setCreatedAt(existing.getCreatedAt());
                    user.setUpdatedAt(Instant.now());
                    log.info("Updating GofpUser for coreUserId={}", user.getCoreUserId());
                    return userRepository.save(user);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    if (user.getId() == null) user.setId(UUID.randomUUID());
                    user.setCreatedAt(Instant.now());
                    user.setUpdatedAt(Instant.now());
                    log.info("Creating GofpUser for coreUserId={}", user.getCoreUserId());
                    return userRepository.save(user);
                }));
    }

    @Override
    public Mono<GofpUser> findById(UUID id) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpUser not found: " + id)));
    }

    @Override
    public Mono<GofpUser> findByCoreUserId(UUID coreUserId) {
        return userRepository.findByCoreUserId(coreUserId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpUser not found for coreUserId: " + coreUserId)));
    }

    @Override
    public Mono<GofpUser> findByEmail(String email) {
        return userRepository.findByEmail(email)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpUser not found for email: " + email)));
    }

    @Override
    public Flux<GofpUser> findAll() {
        return userRepository.findAll();
    }

    @Override
    public Mono<GofpUser> updateStatus(UUID id, UserStatus status) {
        return userRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("GofpUser not found: " + id)))
                .flatMap(user -> {
                    user.setStatus(status);
                    user.setIsActive(status == UserStatus.ACTIVE);
                    user.setUpdatedAt(Instant.now());
                    log.info("GofpUser {} status → {}", id, status);
                    return userRepository.save(user);
                });
    }

    @Override
    public Mono<Void> delete(UUID id) {
        return userRepository.deleteById(id);
    }
}
