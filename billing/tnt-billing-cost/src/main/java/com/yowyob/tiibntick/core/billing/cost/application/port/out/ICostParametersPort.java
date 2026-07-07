package com.yowyob.tiibntick.core.billing.cost.application.port.out;

import com.yowyob.tiibntick.core.billing.cost.domain.model.CostParameters;
import reactor.core.publisher.Mono;
import java.util.UUID;

/**
 * Secondary port — fetches tenant-specific cost parameters from tnt-settings-core.
 * Falls back to CostParameters.defaultForCameroon() when not configured.
 *
 * @author MANFOUO Braun
 */
public interface ICostParametersPort {

    /**
     * Fetches calibrated cost parameters for the given tenant.
     * @param tenantId tenant identifier
     * @return CostParameters (never empty — returns defaults when not configured)
     */
    Mono<CostParameters> getForTenant(UUID tenantId);
}
