package com.yowyob.tiibntick.core.administration.onboarding.domain.model;

import java.util.UUID;

/**
 * Response body for {@code POST .../assign-owner-role}.
 *
 * @author MANFOUO Braun
 */
public record AssignAgencyOwnerRoleResponse(
        UUID roleId,
        UUID assignmentId
) {
}
