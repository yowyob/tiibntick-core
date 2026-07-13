package com.yowyob.tiibntick.core.roles.adapter.out.kernel;

import com.yowyob.tiibntick.common.kernel.KernelResponses;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleAssignmentPort;
import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter implementing {@link ITntRoleAssignmentPort} by calling the Kernel's
 * {@code POST /api/roles/assignments} over HTTP.
 *
 * <p>Uses {@code kernelTpWebClient} (defined in {@code tnt-bootstrap}'s
 * {@code KernelBridgeConfig}) rather than the plain {@code kernelWebClient} used by
 * {@link KernelRoleProvisioningAdapter} — its {@code propagateBearerToken} filter
 * forwards the calling admin's own JWT to the Kernel, so the assignment is attributed
 * to them in the Kernel's audit trail rather than to TiiBnTick's service identity.
 *
 * @author MANFOUO Braun
 */
public class KernelRoleAssignmentAdapter implements ITntRoleAssignmentPort {

    private static final Logger log = LoggerFactory.getLogger(KernelRoleAssignmentAdapter.class);

    private final WebClient kernelTpWebClient;
    private final ITntRoleProvisioningPort provisioningPort;
    private final UUID systemTenantId;

    public KernelRoleAssignmentAdapter(WebClient kernelTpWebClient,
                                        ITntRoleProvisioningPort provisioningPort,
                                        UUID systemTenantId) {
        this.kernelTpWebClient = kernelTpWebClient;
        this.provisioningPort = provisioningPort;
        this.systemTenantId = systemTenantId;
    }

    @Override
    public Mono<UUID> assignRole(UUID targetUserId, String roleCode, String scopeType, UUID scopeId) {
        // Role definitions always live under the system tenant (see TntRoleInitializationService),
        // regardless of which tenant/org/agency the assignment itself is scoped to.
        return provisioningPort.findRoleId(systemTenantId, roleCode)
                .switchIfEmpty(Mono.error(TntRoleException.roleNotFoundInKernel(roleCode)))
                .flatMap(roleId -> doAssign(targetUserId, roleId, scopeType, scopeId));
    }

    private Mono<UUID> doAssign(UUID targetUserId, UUID roleId, String scopeType, UUID scopeId) {
        KernelAssignRoleRequest request = new KernelAssignRoleRequest(
                targetUserId, roleId, scopeType, scopeId, scopeType + ":" + scopeId);
        var responseSpec = kernelTpWebClient
                .post()
                .uri("/api/roles/assignments")
                .bodyValue(request)
                .retrieve();
        return KernelResponses.unwrapObjectOrPropagate(responseSpec, KernelUserRoleAssignmentResponse.class)
                .map(KernelUserRoleAssignmentResponse::id)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Kernel returned no assignment id for role {} → user {}", roleId, targetUserId);
                    return Mono.error(TntRoleException.assignmentFailed(roleId.toString(), targetUserId));
                }));
    }

    /** Matches the Kernel's {@code AssignRoleToUserRequest} schema. */
    record KernelAssignRoleRequest(UUID userId, UUID roleId, String scopeType, UUID scopeId, String scope) {
    }

    /** Matches the Kernel's {@code UserRoleAssignmentResponse} schema. */
    record KernelUserRoleAssignmentResponse(UUID id, UUID tenantId, UUID userId, UUID roleId,
                                             String scopeType, UUID scopeId, String scope) {
    }
}
