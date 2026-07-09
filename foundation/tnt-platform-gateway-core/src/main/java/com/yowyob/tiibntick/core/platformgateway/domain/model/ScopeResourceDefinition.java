package com.yowyob.tiibntick.core.platformgateway.domain.model;

import java.util.Set;

/**
 * One known scope "resource" — a gateway block ({@code AUTH}, {@code SSO},
 * {@code ONBOARDING}) or a curated business-module proxy ({@code DELIVERY}, ...) — with
 * the actions currently meaningful for it. Not a closed Java enum: new resources are
 * added to {@code PlatformScopeRegistry}'s list, no change needed to
 * {@code PermissionMatcher} or the scope string format itself (see
 * {@code docs/auth/platform-client-management-design.md} §2.4/§2.6).
 *
 * @author MANFOUO Braun
 */
public record ScopeResourceDefinition(
        String code,
        String description,
        Set<String> availableActions
) {
}
