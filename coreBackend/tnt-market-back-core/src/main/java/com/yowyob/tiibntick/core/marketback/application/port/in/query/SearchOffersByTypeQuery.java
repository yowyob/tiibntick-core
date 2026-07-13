package com.yowyob.tiibntick.core.marketback.application.port.in.query;

import com.yowyob.tiibntick.core.marketback.domain.model.ServiceType;

/**
 * Query — search active service offers by type.
 * @author MANFOUO Braun
 */
public record SearchOffersByTypeQuery(
        String tenantId,
        ServiceType serviceType,
        String city,
        int page, int size
) {}
