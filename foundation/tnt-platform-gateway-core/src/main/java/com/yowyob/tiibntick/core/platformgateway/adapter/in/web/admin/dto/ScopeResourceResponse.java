package com.yowyob.tiibntick.core.platformgateway.adapter.in.web.admin.dto;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ScopeResourceDefinition;

import java.util.Set;

/**
 * Response body for {@code GET /api/v1/admin/scope-registry} — lets an admin UI build a
 * checkbox list of valid scopes instead of hand-typing {@code resource:action} strings.
 *
 * @author MANFOUO Braun
 */
public record ScopeResourceResponse(
        String code,
        String description,
        Set<String> availableActions
) {
    public static ScopeResourceResponse from(ScopeResourceDefinition definition) {
        return new ScopeResourceResponse(definition.code(), definition.description(), definition.availableActions());
    }
}
