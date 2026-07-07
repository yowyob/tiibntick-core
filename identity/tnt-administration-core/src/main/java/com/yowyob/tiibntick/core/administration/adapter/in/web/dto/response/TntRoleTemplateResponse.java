package com.yowyob.tiibntick.core.administration.adapter.in.web.dto.response;

import com.yowyob.tiibntick.core.administration.domain.service.TntRoleTemplateRegistry.TntRoleTemplate;

import java.util.Set;

/**
 * API response DTO for a TiiBnTick role template.
 * Author: MANFOUO Braun
 */
public record TntRoleTemplateResponse(
        String code, String name, String scopeType,
        Set<String> permissions, boolean protectedTemplate) {

    public static TntRoleTemplateResponse from(TntRoleTemplate t) {
        return new TntRoleTemplateResponse(t.code(), t.name(), t.scopeType(),
                t.permissions(), t.protectedTemplate());
    }
}
