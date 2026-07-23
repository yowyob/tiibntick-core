package com.yowyob.tiibntick.core.roles.adapter.out.kernel;

import com.yowyob.tiibntick.core.roles.application.port.out.ITntRoleProvisioningPort;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
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
}
