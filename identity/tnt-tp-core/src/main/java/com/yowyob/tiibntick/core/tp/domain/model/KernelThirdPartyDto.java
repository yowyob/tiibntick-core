package com.yowyob.tiibntick.core.tp.domain.model;

import java.util.UUID;

/**
 * Read-only DTO representing the Kernel ThirdParty entity fetched from RT-comops-tp-core.
 *
 * <p>This record is <strong>never persisted</strong> in the TiiBnTick database.
 * It is used exclusively to validate the existence of a Kernel ThirdParty before
 * creating a {@link TntClientProfile} or other tp-linked aggregate.
 *
 * <p>Integration rule: TiiBnTick does not extend Kernel classes via Java inheritance.
 * Each TiiBnTick aggregate references the Kernel entity via its {@link UUID} key
 * ({@code thirdPartyId}), and this DTO carries the fetched Kernel data when needed.
 *
 * @param thirdPartyId  Unique Kernel third-party identifier (primary reference key)
 * @param displayName   ThirdParty display name as registered in the Kernel
 * @param email         Contact email (may be null for informal actors)
 * @param country       ISO 3166-1 alpha-2 country code (e.g., "CM", "NG", "KE")
 * @param active        Whether the Kernel ThirdParty is currently active
 *
 * @author MANFOUO Braun
 */
public record KernelThirdPartyDto(
        UUID thirdPartyId,
        String displayName,
        String email,
        String country,
        boolean active
) {

    /**
     * Creates a minimal DTO for cases where only the existence check result is needed.
     *
     * @param thirdPartyId the Kernel third-party UUID
     * @param displayName  the display name
     * @return a minimal {@link KernelThirdPartyDto}
     */
    public static KernelThirdPartyDto minimal(UUID thirdPartyId, String displayName) {
        return new KernelThirdPartyDto(thirdPartyId, displayName, null, null, true);
    }
}
