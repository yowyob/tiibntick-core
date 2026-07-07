package com.yowyob.tiibntick.core.billing.report.application.port.out;

import com.yowyob.tiibntick.core.billing.report.domain.model.BillingKPISnapshot;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Output port: persistence for BillingKPISnapshot.
 *
 * @author MANFOUO Braun
 */
public interface BillingKPISnapshotRepository {
    Mono<BillingKPISnapshot> save(BillingKPISnapshot snapshot);
    Mono<BillingKPISnapshot> findLatestByTenantId(UUID tenantId);
}
