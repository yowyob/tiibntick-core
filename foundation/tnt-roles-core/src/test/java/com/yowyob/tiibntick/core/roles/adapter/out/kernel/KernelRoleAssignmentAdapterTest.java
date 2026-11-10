package com.yowyob.tiibntick.core.roles.adapter.out.kernel;

import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import com.yowyob.tiibntick.core.roles.domain.model.TntRoleDefinition;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link KernelRoleAssignmentAdapter} against a {@link MockWebServer} —
 * focused on {@link KernelRoleAssignmentAdapter#revokeAssignment(UUID)}, the
 * {@code DELETE /api/roles/assignments/{id}} operation added for Chantier D / Audit n°6 S5.
 *
 * @author MANFOUO Braun
 */
class KernelRoleAssignmentAdapterTest {

    private MockWebServer mockWebServer;
    private KernelRoleAssignmentAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        // provisioningPort/systemTenantId are unused by revokeAssignment — a mock is enough.
        adapter = new KernelRoleAssignmentAdapter(webClient, mock(ITntRoleProvisioningPort.class), UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("revokeAssignment sends DELETE /api/roles/assignments/{id} and completes on 204")
    void revokeAssignmentSendsDeleteAndCompletes() throws InterruptedException {
        UUID assignmentId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        StepVerifier.create(adapter.revokeAssignment(assignmentId))
                .verifyComplete();

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getPath()).isEqualTo("/api/roles/assignments/" + assignmentId);
    }

    @Test
    @DisplayName("revokeAssignment treats a 404 (already revoked) as an idempotent success, not an error")
    void revokeAssignmentTreatsNotFoundAsNoOp() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        StepVerifier.create(adapter.revokeAssignment(UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    @DisplayName("revokeAssignment propagates other Kernel errors as TntRoleException rather than swallowing them")
    void revokeAssignmentPropagatesOtherErrorsAsDomainException() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.revokeAssignment(UUID.randomUUID()))
                .expectErrorMatches(e -> e instanceof TntRoleException
                        && ((TntRoleException) e).getCode().equals("ROLE_REVOCATION_FAILED"))
                .verify();
    }

    // ── assignRoleInCallerTenant ────────────────────────────────────────────────

    private static TntRoleDefinition freelancerDefinition() {
        return TntRoleDefinition.from(TntRole.FREELANCER);
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    @DisplayName("assignRoleInCallerTenant provisions the missing role in the caller's tenant, then grants it")
    void assignInCallerTenantCreatesMissingRoleThenAssigns() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        // GET /api/roles → bare array without FREELANCER (the Kernel documents RoleResponse[])
        mockWebServer.enqueue(json(200, "[{\"id\":\"" + UUID.randomUUID() + "\",\"code\":\"OWNER\",\"name\":\"Owner\"}]"));
        // POST /api/roles → ApiResponseRoleResponse envelope
        mockWebServer.enqueue(json(200, "{\"success\":true,\"data\":{\"id\":\"" + roleId + "\",\"code\":\"FREELANCER\",\"name\":\"Freelancer Deliverer\"}}"));
        // GET /api/roles/assignments?userId= → nothing yet
        mockWebServer.enqueue(json(200, "[]"));
        // POST /api/roles/assignments → envelope
        mockWebServer.enqueue(json(200, "{\"success\":true,\"data\":{\"id\":\"" + assignmentId + "\",\"userId\":\"" + userId + "\",\"roleId\":\"" + roleId + "\"}}"));

        StepVerifier.create(adapter.assignRoleInCallerTenant(userId, freelancerDefinition(), "TENANT", tenantId))
                .expectNext(assignmentId)
                .verifyComplete();

        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/api/roles");
        RecordedRequest create = mockWebServer.takeRequest();
        assertThat(create.getMethod()).isEqualTo("POST");
        assertThat(create.getPath()).isEqualTo("/api/roles");
        assertThat(create.getBody().readUtf8())
                .contains("\"code\":\"FREELANCER\"").contains("\"scopeType\":\"TENANT\"").contains("wallet:read");
        assertThat(mockWebServer.takeRequest().getPath()).isEqualTo("/api/roles/assignments?userId=" + userId);
        RecordedRequest assign = mockWebServer.takeRequest();
        assertThat(assign.getMethod()).isEqualTo("POST");
        assertThat(assign.getPath()).isEqualTo("/api/roles/assignments");
        assertThat(assign.getBody().readUtf8())
                .contains("\"roleId\":\"" + roleId + "\"").contains("\"userId\":\"" + userId + "\"");
    }

    @Test
    @DisplayName("assignRoleInCallerTenant is idempotent: an existing role and assignment are reused, nothing is created")
    void assignInCallerTenantReusesExistingAssignment() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        mockWebServer.enqueue(json(200, "{\"success\":true,\"data\":[{\"id\":\"" + roleId + "\",\"code\":\"FREELANCER\",\"name\":\"x\"}]}"));
        mockWebServer.enqueue(json(200, "[{\"id\":\"" + assignmentId + "\",\"userId\":\"" + userId + "\",\"roleId\":\"" + roleId + "\"}]"));

        StepVerifier.create(adapter.assignRoleInCallerTenant(userId, freelancerDefinition(), "TENANT", UUID.randomUUID()))
                .expectNext(assignmentId)
                .verifyComplete();

        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("assignRoleInCallerTenant propagates a Kernel refusal instead of reporting success")
    void assignInCallerTenantPropagatesKernelRefusal() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));

        StepVerifier.create(adapter.assignRoleInCallerTenant(UUID.randomUUID(), freelancerDefinition(), "TENANT", UUID.randomUUID()))
                .expectError(org.springframework.web.reactive.function.client.WebClientResponseException.Forbidden.class)
                .verify();
    }
}
