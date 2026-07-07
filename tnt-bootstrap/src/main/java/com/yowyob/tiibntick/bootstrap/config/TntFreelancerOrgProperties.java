package com.yowyob.tiibntick.bootstrap.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Configuration properties for the FreelancerOrganization feature ().
 *
 * <p>Bound from {@code application.yml} under the {@code tnt.freelancer-org} prefix.
 *
 * <p>These properties control platform-wide limits and KYC requirements
 * for FreelancerOrganizations across all tenants.
 *
 * <p>Example configuration:
 * <pre>{@code
 * tnt:
 *   freelancer-org:
 *     max-sub-deliverers: 5
 *     kyc-basic-required-docs: [NATIONAL_ID_PHOTO]
 *     kyc-full-required-docs: [NATIONAL_ID_PHOTO, VEHICLE_REGISTRATION, INSURANCE]
 *     max-vehicles-per-org: 3
 *     max-equipments-per-org: 10
 *     tenant-prefix: "FRL-"
 * }</pre>
 *
 * @author MANFOUO Braun
 */
@Component
@ConfigurationProperties(prefix = "tnt.freelancer-org")
@Getter
@Setter
public class TntFreelancerOrgProperties {

    /**
     * Maximum number of sub-deliverers allowed per FreelancerOrg.
     * Default: 5.
     */
    private int maxSubDeliverers = 5;

    /**
     * Documents required for KYC BASIC level verification.
     * Default: [NATIONAL_ID_PHOTO].
     */
    private List<String> kycBasicRequiredDocs = List.of("NATIONAL_ID_PHOTO");

    /**
     * Documents required for KYC FULL level verification.
     * Default: [NATIONAL_ID_PHOTO, VEHICLE_REGISTRATION, INSURANCE].
     */
    private List<String> kycFullRequiredDocs = List.of(
            "NATIONAL_ID_PHOTO", "VEHICLE_REGISTRATION", "INSURANCE");

    /**
     * Maximum number of vehicles allowed per FreelancerOrg fleet.
     * Default: 3 (benskin operators rarely have more).
     */
    private int maxVehiclesPerOrg = 3;

    /**
     * Maximum number of equipment items allowed per FreelancerOrg.
     * Default: 10.
     */
    private int maxEquipmentsPerOrg = 10;

    /**
     * Tenant ID prefix for FreelancerOrg tenants.
     * TenantIds starting with this prefix are handled with FreelancerOrg isolation policy.
     * Default: "FRL-".
     */
    private String tenantPrefix = "FRL-";
}
