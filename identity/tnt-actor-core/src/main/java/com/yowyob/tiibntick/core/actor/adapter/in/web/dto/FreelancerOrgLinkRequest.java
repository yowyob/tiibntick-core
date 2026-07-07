package com.yowyob.tiibntick.core.actor.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * REST request body for manually linking a freelancer profile to a
 * FreelancerOrganization (admin or system endpoint).
 *
 * <p>In the normal flow, the linking is done automatically via the
 * {@code FreelancerOrgEventConsumer} (Kafka). This DTO enables manual
 * admin operations (e.g., repair, migration).
 *
 * @param freelancerOrgId UUID of the FreelancerOrganization to link to
 *
 * @author MANFOUO Braun
 */
public record FreelancerOrgLinkRequest(
        @NotNull(message = "freelancerOrgId is required")
        UUID freelancerOrgId) {
}
