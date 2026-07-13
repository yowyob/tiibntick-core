package com.yowyob.tiibntick.core.agency.staff.adapter.out.persistence;

import com.yowyob.tiibntick.core.agency.staff.adapter.out.persistence.entity.StaffMemberEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface StaffMemberR2dbcRepository extends ReactiveCrudRepository<StaffMemberEntity, UUID> {

    Mono<StaffMemberEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    Flux<StaffMemberEntity> findByAgencyIdAndTenantId(UUID agencyId, UUID tenantId);

    @Query("SELECT * FROM agency_hr.staff_members WHERE tenant_id = :tenantId AND LOWER(email) = LOWER(:email) LIMIT 1")
    Mono<StaffMemberEntity> findByTenantIdAndEmailIgnoreCase(UUID tenantId, String email);
}
