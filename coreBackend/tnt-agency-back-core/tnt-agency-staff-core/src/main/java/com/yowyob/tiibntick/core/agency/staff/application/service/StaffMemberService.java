package com.yowyob.tiibntick.core.agency.staff.application.service;

import com.yowyob.tiibntick.common.exception.TntNotFoundException;
import com.yowyob.tiibntick.common.exception.TntValidationException;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.AgencyRegistryR2dbcRepository;
import com.yowyob.tiibntick.core.agency.org.adapter.out.persistence.entity.AgencyRegistryEntity;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import com.yowyob.tiibntick.core.agency.staff.adapter.in.web.dto.StaffMemberResponse;
import com.yowyob.tiibntick.core.agency.staff.adapter.out.persistence.StaffMemberR2dbcRepository;
import com.yowyob.tiibntick.core.agency.staff.application.mapper.StaffMemberMapper;
import com.yowyob.tiibntick.core.agency.staff.domain.AgencyStaffMember;
import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffRole;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
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
    private final AgencyRegistryService agencyRegistryService;
    private final AgencyCredentialsProvisioningService credentialsProvisioning;

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
        Mono<AgencyRegistryEntity> agencyMono = input.provisionCredentials()
                ? agencyRegistryService.ensureKernelOrganization(input.tenantId(), input.agencyId())
                : requireAgencyEntity(input.agencyId(), input.tenantId());

        return agencyMono
                .flatMap(agency -> {
                    Mono<Void> provision = input.provisionCredentials()
                            ? credentialsProvisioning.provision(
                                    new AgencyCredentialsProvisioningService.ProvisionRequest(
                                            input.tenantId(),
                                            input.agencyId(),
                                            agency.getKernelOrganizationId(),
                                            agency.getCoreAgencyId(),
                                            input.fullName(),
                                            input.email(),
                                            mapStaffRoleToTnt(input.role()),
                                            staffRoleLabel(input.role()))).then()
                            : Mono.empty();

                    Instant now = Instant.now();
                    AgencyStaffMember member = AgencyStaffMember.register(
                            UUID.randomUUID(), input.tenantId(), input.agencyId(), input.branchId(),
                            input.fullName(), input.phone(), input.email(), input.role(), now
                    );
                    return provision.then(staffRepo.save(StaffMemberMapper.toEntity(member)));
                })
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
        return requireAgencyEntity(agencyId, tenantId).then();
    }

    private Mono<AgencyRegistryEntity> requireAgencyEntity(UUID agencyId, UUID tenantId) {
        return agencyRepo.findByIdAndTenantId(agencyId, tenantId)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "AGENCY_NOT_FOUND", "Agency not found: " + agencyId)));
    }

    private Mono<AgencyStaffMember> requireMember(UUID memberId, UUID tenantId) {
        return staffRepo.findByIdAndTenantId(memberId, tenantId)
                .map(StaffMemberMapper::toDomain)
                .switchIfEmpty(Mono.error(new TntNotFoundException(
                        "STAFF_NOT_FOUND", "Staff member not found: " + memberId)));
    }

    /**
     * Maps ERP staff roles to TNT JWT roles used by Agency portals.
     * Roles without a dedicated TNT enum fall back to {@link TntRole#BRANCH_MANAGER}.
     */
    static String mapStaffRoleToTnt(StaffRole role) {
        if (role == null) {
            throw new TntValidationException("role is required");
        }
        return switch (role) {
            case AGENCY_MANAGER -> TntRole.AGENCY_MANAGER.code();
            case BRANCH_MANAGER -> TntRole.BRANCH_MANAGER.code();
            case HUB_OPERATOR -> TntRole.AGENCY_HUB_OPERATOR.code();
            case OPERATIONS_MANAGER, ACCOUNTANT, DISPATCHER -> TntRole.BRANCH_MANAGER.code();
        };
    }

    static String staffRoleLabel(StaffRole role) {
        if (role == null) {
            return "membre du personnel";
        }
        return switch (role) {
            case AGENCY_MANAGER -> "responsable d'agence";
            case BRANCH_MANAGER -> "responsable d'antenne";
            case OPERATIONS_MANAGER -> "responsable opérations";
            case ACCOUNTANT -> "comptable";
            case DISPATCHER -> "dispatcher";
            case HUB_OPERATOR -> "gérant de hub";
        };
    }

    public record RegisterInput(
            UUID tenantId, UUID agencyId, UUID branchId,
            String fullName, String phone, String email, StaffRole role,
            boolean provisionCredentials) {

        public RegisterInput(
                UUID tenantId, UUID agencyId, UUID branchId,
                String fullName, String phone, String email, StaffRole role) {
            this(tenantId, agencyId, branchId, fullName, phone, email, role, true);
        }
    }

    public record UpdateInput(
            UUID tenantId, UUID memberId,
            String fullName, String phone, String email, StaffRole role, UUID branchId) {}
}
