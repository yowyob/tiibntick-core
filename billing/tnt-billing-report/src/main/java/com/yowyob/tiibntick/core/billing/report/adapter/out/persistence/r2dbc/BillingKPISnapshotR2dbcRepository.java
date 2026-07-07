package com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.r2dbc;

import com.yowyob.tiibntick.core.billing.report.adapter.out.persistence.entity.BillingKPISnapshotEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for BillingKPISnapshotEntity.
 *
 * @author MANFOUO Braun
 */
public interface BillingKPISnapshotR2dbcRepository
        extends ReactiveCrudRepository<BillingKPISnapshotEntity, UUID> {

    @Query("SELECT * FROM tnt_billing_kpi_snapshots WHERE tenant_id = :tenantId ORDER BY snapshot_at DESC LIMIT 1")
    Mono<BillingKPISnapshotEntity> findLatestByTenantId(UUID tenantId);
}
