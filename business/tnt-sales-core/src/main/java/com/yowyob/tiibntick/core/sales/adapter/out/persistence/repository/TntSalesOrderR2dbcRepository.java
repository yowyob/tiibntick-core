package com.yowyob.tiibntick.core.sales.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.sales.adapter.out.persistence.entity.TntSalesOrderEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for TntSalesOrder entities.
 * Author: MANFOUO Braun
 */
public interface TntSalesOrderR2dbcRepository extends ReactiveCrudRepository<TntSalesOrderEntity, UUID> {

    @Query("SELECT * FROM sales.orders WHERE tenant_id = :tenantId AND id = :id")
    Mono<TntSalesOrderEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT COUNT(*) > 0 FROM sales.orders WHERE tenant_id = :tenantId AND organization_id = :organizationId AND order_number = :orderNumber")
    Mono<Boolean> existsByTenantIdAndOrganizationIdAndOrderNumber(UUID tenantId, UUID organizationId, String orderNumber);

    @Query("SELECT * FROM sales.orders WHERE tenant_id = :tenantId AND client_third_party_id = :clientId ORDER BY created_at DESC")
    Flux<TntSalesOrderEntity> findByTenantIdAndClientThirdPartyId(UUID tenantId, UUID clientId);

    @Query("""
           SELECT * FROM sales.orders
           WHERE tenant_id = :tenantId AND agency_id = :agencyId
             AND EXTRACT(YEAR FROM created_at) = :year
             AND EXTRACT(MONTH FROM created_at) = :month
           ORDER BY created_at DESC
           """)
    Flux<TntSalesOrderEntity> findByTenantIdAndAgencyIdAndPeriod(UUID tenantId, UUID agencyId, int year, int month);

    @Query("SELECT * FROM sales.orders WHERE tenant_id = :tenantId AND status = :status ORDER BY priority DESC, created_at ASC")
    Flux<TntSalesOrderEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
