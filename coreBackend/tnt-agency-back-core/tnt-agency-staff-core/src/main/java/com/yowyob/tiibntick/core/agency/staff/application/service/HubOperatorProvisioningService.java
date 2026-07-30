package com.yowyob.tiibntick.core.agency.staff.application.service;

import com.yowyob.tiibntick.core.agency.org.adapter.in.web.dto.AgencyRelayHubResponse;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRegistryService;
import com.yowyob.tiibntick.core.agency.org.application.service.AgencyRelayHubService;
import com.yowyob.tiibntick.core.agency.staff.adapter.in.web.dto.StaffMemberResponse;
import com.yowyob.tiibntick.core.agency.staff.domain.vo.StaffRole;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Registers a hub operator (gérant) with Kernel credentials and links them to a hub.
 */
@Service
@RequiredArgsConstructor
public class HubOperatorProvisioningService {

    private final AgencyCredentialsProvisioningService credentialsProvisioning;
    private final StaffMemberService staffMemberService;
    private final AgencyRelayHubService hubService;
    private final AgencyRegistryService agencyRegistryService;

    public record ProvisionOperatorInput(
            UUID tenantId,
            UUID agencyId,
            UUID hubId,
            UUID branchId,
            String fullName,
            String phone,
            String email) {}

    public record ProvisionOperatorResult(
            StaffMemberResponse staff,
            AgencyRelayHubResponse hub,
            UUID operatorUserId) {}

    @Transactional
    public Mono<ProvisionOperatorResult> provision(ProvisionOperatorInput input) {
        return agencyRegistryService.ensureKernelOrganization(input.tenantId(), input.agencyId())
                .flatMap(agency -> hubService.getById(input.tenantId(), input.hubId())
                        .flatMap(hub -> credentialsProvisioning.provision(
                                        new AgencyCredentialsProvisioningService.ProvisionRequest(
                                                input.tenantId(),
                                                input.agencyId(),
                                                agency.getKernelOrganizationId(),
                                                agency.getCoreAgencyId(),
                                                input.fullName(),
                                                input.email(),
                                                TntRole.AGENCY_HUB_OPERATOR.code(),
                                                "gérant de hub"))
                                .flatMap(access -> staffMemberService.register(
                                                new StaffMemberService.RegisterInput(
                                                        input.tenantId(),
                                                        input.agencyId(),
                                                        input.branchId() != null
                                                                ? input.branchId()
                                                                : hub.branchId(),
                                                        input.fullName(),
                                                        input.phone(),
                                                        input.email(),
                                                        StaffRole.HUB_OPERATOR,
                                                        false))
                                        .flatMap(staff -> hubService.assignOperator(
                                                        input.tenantId(),
                                                        input.hubId(),
                                                        access.userId(),
                                                        input.email(),
                                                        input.fullName())
                                                .map(updatedHub -> new ProvisionOperatorResult(
                                                        staff, updatedHub, access.userId()))))));
    }
}
