package com.yowyob.tiibntick.core.actor.adapter.out.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC entity mapping for the {@code tnt_actor.freelancer_profiles} table.
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@link #incidentHistoryCount} — for tnt-incident-core integration.</li>
 * </ul>
 *
 * <h3> additions — FreelancerOrganization link</h3>
 * <ul>
 *   <li>{@link #freelancerOrgId} — UUID of the linked FreelancerOrganization
 *       (nullable). References {@code tnt_freelancer_organization.id} logically.</li>
 *   <li>{@link #roleInOrg} — VARCHAR enum name: "OWNER" or "SUB_DELIVERER" (nullable).</li>
 *   <li>{@link #isOrgVerified} — cached org verification flag (false by default).</li>
 * </ul>
 * See Liquibase migration {@code V3__add_freelancer_org_link_to_actor.sql}.
 *
 * @author MANFOUO Braun
 */
@Table(schema = "tnt_actor", name = "freelancer_profiles")
public record FreelancerProfileEntity(
        @Id UUID id,
        UUID tenantId,
        UUID actorId,
        String actorStatus,
        String kycStatus,
        Double locationLat,
        Double locationLng,
        Double locationAccuracy,
        Instant locationTimestamp,
        String locationSource,
        double ratingScore,
        int ratingTotal,
        Instant ratingUpdatedAt,
        String badgesJson,
        Instant createdAt,
        Instant updatedAt,
        String serviceZoneIdsJson,
        String availabilitySlotsJson,
        UUID pricingPolicyId,
        String associatedAgencyIdsJson,
        /** Number of incidents this freelancer was involved in. Added in . */
        int incidentHistoryCount,
        /** UUID of the linked FreelancerOrganization. Nullable. Added in . */
        UUID freelancerOrgId,
        /** Role within the org: "OWNER" or "SUB_DELIVERER". Nullable. Added in . */
        String roleInOrg,
        /** Cached org verification status. Added in . */
        boolean isOrgVerified) {
}
