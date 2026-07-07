package com.yowyob.tiibntick.core.organization.domain.model;

import java.util.UUID;

/**
 * Read-only DTO representing the Kernel Organization entity fetched from RT-comops-organization-core.
 *
 * <p>This record is <strong>never persisted</strong> in the TiiBnTick database.
 * It is used exclusively to validate the existence of a Kernel organization before
 * creating or updating a TiiBnTick organizational structure (Agency, Branch, RelayHub).
 *
 * <p>Integration rule: TiiBnTick does not extend Kernel classes via Java inheritance.
 * Instead, each TiiBnTick aggregate references the Kernel entity via its {@link UUID} key
 * ({@code organizationId}), and this DTO carries the fetched Kernel data when needed.
 *
 * @param organizationId   Unique Kernel organization identifier (primary reference key)
 * @param name             Organization name as registered in the Kernel
 * @param legalName        Full legal name of the organization
 * @param country          ISO 3166-1 alpha-2 country code (e.g., "CM", "NG", "KE")
 * @param active           Whether the Kernel organization is currently active
 *
 * @author MANFOUO Braun
 */
public record KernelOrganizationDto(
        UUID organizationId,
        String name,
        String legalName,
        String country,
        boolean active
) {

    /**
     * Creates a minimal DTO for cases where only the existence check result is needed.
     *
     * @param organizationId the Kernel organization UUID
     * @param name           the organization name
     * @return a minimal {@link KernelOrganizationDto}
     */
    public static KernelOrganizationDto minimal(UUID organizationId, String name) {
        return new KernelOrganizationDto(organizationId, name, null, null, true);
    }
}
