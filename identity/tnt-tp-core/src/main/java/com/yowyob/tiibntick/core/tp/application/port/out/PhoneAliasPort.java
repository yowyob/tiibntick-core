package com.yowyob.tiibntick.core.tp.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: generates and persists phone aliases for relay-point anonymity.
 *
 * @author MANFOUO Braun
 */
public interface PhoneAliasPort {

    /**
     * Generates a unique phone alias for the given third party.
     * The alias is a formatted local number not associated with any real subscriber.
     *
     * @param tenantId     the owning tenant
     * @param thirdPartyId the third party needing anonymization
     * @return a generated alias phone string
     */
    Mono<String> generateAlias(UUID tenantId, UUID thirdPartyId);

    /**
     * Resolves the real phone number behind an alias (restricted access).
     *
     * @param alias the phone alias
     * @return the real phone number (only used by authorized hub operators)
     */
    Mono<String> resolveAlias(String alias);

    /**
     * Revokes an existing phone alias.
     *
     * @param tenantId     the owning tenant
     * @param thirdPartyId the third party whose alias should be revoked
     * @return Mono<Void> on completion
     */
    Mono<Void> revokeAlias(UUID tenantId, UUID thirdPartyId);
}
