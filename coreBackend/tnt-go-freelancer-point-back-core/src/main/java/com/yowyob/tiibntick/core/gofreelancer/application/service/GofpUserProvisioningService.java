package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.user.UserStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Provisions a minimal {@code gofp_users} row on first authenticated call.
 *
 * <p>The row uses {@code coreUserId = JWT sub} by construction, ensuring that
 * GPS-write and presence-read paths converge on the same identity key without
 * requiring the user to explicitly register via the now-removed
 * {@code POST /api/auth/register} endpoint.
 *
 * <p>Idempotent: if a row already exists for {@code coreUserId}, it is returned
 * unchanged. The caller (typically {@link GofpUserProvisioningFilter}) may call
 * this on every authenticated request without risk of duplicate rows.
 *
 * @author François-Charles ATANGA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GofpUserProvisioningService {

    private final GofpUserRepository userRepository;

    /**
     * Returns the existing {@code GofpUser} for {@code coreUserId}, or creates
     * a minimal placeholder row if none exists yet.
     *
     * <p>The placeholder uses empty strings for name fields (DB allows them —
     * the user should complete their profile via the profile-update endpoint)
     * and a system-generated email {@code user+<coreUserId>@gofp.provisioned}
     * that satisfies the {@code email NOT NULL UNIQUE} constraint.
     */
    public Mono<GofpUser> provisionIfAbsent(UUID coreUserId) {
        return userRepository.findByCoreUserId(coreUserId)
                .switchIfEmpty(Mono.defer(() -> {
                    GofpUser user = GofpUser.builder()
                            .id(UUID.randomUUID())
                            .coreUserId(coreUserId)
                            .firstName("")
                            .lastName("")
                            .email("user+" + coreUserId + "@gofp.provisioned")
                            .status(UserStatus.ACTIVE)
                            .isActive(true)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();
                    log.info("Auto-provisioning GofpUser for coreUserId={}", coreUserId);
                    return userRepository.save(user);
                }));
    }
}
