package com.yowyob.tiibntick.core.marketback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketListingRepository;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketOrderRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Generic Market KPIs/stats API (admin usage) — listings/orders counts and
 * per-provider revenue, consumed by the Market BFF's admin dashboards.
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Market Stats", description = "Market KPIs and stats (admin usage)")
@RestController
@RequestMapping("/api/v1/platform/market/stats")
@RequiredArgsConstructor
public class MarketStatsController {

    private final IMarketListingRepository listingRepository;
    private final IMarketOrderRepository orderRepository;

    @Operation(summary = "Overview KPIs for the caller's tenant")
    @GetMapping("/overview")
    public Mono<Map<String, Object>> overview(@CurrentUser TntUserIdentity user) {
        String tenantId = user.tenantId().toString();
        return Mono.zip(
                listingRepository.countByTenantId(tenantId),
                orderRepository.countByTenantId(tenantId),
                orderRepository.countCompletedByTenantId(tenantId)
        ).map(t -> Map.of(
                "totalListings", t.getT1(),
                "totalOrders", t.getT2(),
                "completedOrders", t.getT3()
        ));
    }

    @Operation(summary = "Stats for a specific provider")
    @GetMapping("/provider/{providerId}")
    public Mono<Map<String, Object>> providerStats(
            @PathVariable UUID providerId,
            @CurrentUser TntUserIdentity user) {
        String tenantId = user.tenantId().toString();
        return Mono.zip(
                orderRepository.countByProviderIdAndTenantId(providerId, tenantId),
                orderRepository.sumRevenueByProviderId(providerId, tenantId)
        ).map(t -> Map.of(
                "totalOrders", t.getT1(),
                "totalRevenue", t.getT2()
        ));
    }
}
