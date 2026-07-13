package com.yowyob.tiibntick.core.agency.staff.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.staff.adapter.in.web.dto.StaffMemberResponse;
import com.yowyob.tiibntick.core.agency.staff.adapter.out.persistence.StaffMemberR2dbcRepository;
import com.yowyob.tiibntick.core.agency.staff.application.mapper.StaffMemberMapper;
import com.yowyob.tiibntick.core.agency.staff.domain.AgencyStaffMember;
import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * Port of tnt-agency staff use cases ({@code RegisterStaffMemberService}, etc.).
 */
@Service
@RequiredArgsConstructor
public class StaffMemberService {

    private final StaffMemberR2dbcRepository staffRepo;
    private final AgencyRegistryR2dbcRepository agencyRepo;

    public Flux<StaffMemberResponse> listByAgency(UUID tenantId, UUID agencyId) {
        return requireAgency(agencyId, tenantId)
                .thenMany(staffRepo.findByAgencyIdAndTenantId(agencyId, tenantId))
                .map(StaffMemberMapper::toDomain)
                .map(StaffMemberMapper::toResponse);
    }

    public Mono<StaffMemberResponse> findByEmail(UUID tenantId, String email) {
        return staffRepo.findByTenantIdAndEmailIgnoreCase(tenantId, email)
                .map(StaffMemberMapper::toDomain)
                .map(StaffMemberMapper::toResponse);
    }

    @Transactional
    public Mono<StaffMemberResponse> register(RegisterInput input) {
        return requireAgency(input.agencyId(), input.tenantId())
                .then(Mono.defer(() -> {
                    Instant now = Instant.now();
                    AgencyStaffMember member = AgencyStaffMember.register(
                            UUID.randomUUID(), input.tenantId(), input.agencyId(), input.branchId(),
                            input.fullName(), input.phone(), input.email(), input.role(), now
                    );
                    return staffRepo.save(StaffMemberMapper.toEntity(member));
                }))
                .map(StaffMemberMapper::toDomain)
                .map(StaffMemberMapper::toResponse);
    }

    @Transactional
    public Mono<StaffMemberResponse> update(UpdateInput input) {
        return requireMember(input.memberId(), input.tenantId())
                .flatMap(member -> {
                    member.update(input.fullName(), input.phone(), input.email(),
                            input.role(), input.branchId(), Instant.now());
                    return staffRepo.save(StaffMemberMapper.toEntity(member));
                })
                .map(StaffMemberMapper::toDomain)
                .map(StaffMemberMapper::toResponse);
    }

    @Transactional
    public Mono<StaffMemberResponse> suspend(UUID tenantId, UUID memberId) {
        return requireMember(memberId, tenantId)
                .flatMap(member -> {
                    member.suspend(Instant.now());
                    return staffRepo.save(StaffMemberMapper.toEntity(member));
                })
                .map(StaffMemberMapper::toDomain)
                .map(StaffMemberMapper::toResponse);
    }

    @Transactional
    public Mono<StaffMemberResponse> reactivate(UUID tenantId, UUID memberId) {
        return requireMember(memberId, tenantId)
                .flatMap(member -> {
                    member.reactivate(Instant.now());
                    return staffRepo.save(StaffMemberMapper.toEntity(member));
                })
                .map(StaffMemberMapper::toDomain)
                .map(StaffMemberMapper::toResponse);
    }

    private Mono<Void> requireAgency(UUID agencyId, UUID tenantId) {
        return agencyRepo.findByIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_NOT_FOUND", "Agency not found: " + agencyId)))
                .then();
    }

    private Mono<AgencyStaffMember> requireMember(UUID memberId, UUID tenantId) {
        return staffRepo.findByIdAndTenantId(memberId, tenantId)
                .map(StaffMemberMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "STAFF_NOT_FOUND", "Staff member not found: " + memberId)));
    }

    public record RegisterInput(
            UUID tenantId, UUID agencyId, UUID branchId,
            String fullName, String phone, String email, StaffRole role) {}

    public record UpdateInput(
            UUID tenantId, UUID memberId,
            String fullName, String phone, String email, StaffRole role, UUID branchId) {}
}
