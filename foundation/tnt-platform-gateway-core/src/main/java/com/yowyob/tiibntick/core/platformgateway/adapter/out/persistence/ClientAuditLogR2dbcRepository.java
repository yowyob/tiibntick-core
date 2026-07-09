package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ClientAuditLogEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Spring Data R2DBC repository for {@code tnt_client_audit_logs}. Filtered/paginated
 * listing lives in {@code ClientAuditLogRepositoryAdapter} (via {@code R2dbcEntityTemplate}).
 *
 * @author MANFOUO Braun
 */
public interface ClientAuditLogR2dbcRepository extends ReactiveCrudRepository<ClientAuditLogEntity, String> {
}
