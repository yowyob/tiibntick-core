package com.yowyob.tiibntick.core.billing.cost.adapter.out.settings;

import com.yowyob.tiibntick.core.billing.cost.application.port.out.ICostParametersPort;
import com.yowyob.tiibntick.core.billing.cost.domain.model.CostParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Map;
import java.util.UUID;

/**
 * CostParametersAdapter — fetches tenant-specific CostParameters from tnt-settings-core.
 * Falls back to Cameroon defaults when not configured or unavailable.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CostParametersAdapter implements ICostParametersPort {

    @Qualifier("kernelWebClient")
    private final WebClient settingsCoreWebClient;

    @Override
    public Mono<CostParameters> getForTenant(UUID tenantId) {
        return settingsCoreWebClient.get()
                .uri("/settings/cost-parameters?tenantId={tenantId}", tenantId)
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::fromMap)
                .doOnError(e -> log.warn(
                        "Failed to fetch CostParameters for tenantId={}: {} — using defaults",
                        tenantId, e.getMessage()))
                .onErrorReturn(CostParameters.defaultForCameroon());
    }

    private CostParameters fromMap(Map<String, Object> map) {
        return CostParameters.builder()
                .fuelPricePerLitre(getDecimal(map, "fuelPricePerLitre", "730"))
                .fuelConsumptionL100km(getDecimal(map, "fuelConsumptionL100km", "2.5"))
                .vehicleWearCostPerKm(getDecimal(map, "vehicleWearCostPerKm", "50"))
                .driverTimeValueXAFPerMin(getDecimal(map, "driverTimeValueXAFPerMin", "15"))
                .penibilityBaseCostXAF(getDecimal(map, "penibilityBaseCostXAF", "200"))
                .rainSurchargeCostXAF(getDecimal(map, "rainSurchargeCostXAF", "300"))
                .floodSurchargeCostXAF(getDecimal(map, "floodSurchargeCostXAF", "1000"))
                .loadSensitivity(getDecimal(map, "loadSensitivity", "0.20"))
                .currency(Currency.getInstance((String) map.getOrDefault("currency", "XAF")))
                .build();
    }

    private BigDecimal getDecimal(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) return new BigDecimal(fallback);
        return new BigDecimal(value.toString());
    }
}
