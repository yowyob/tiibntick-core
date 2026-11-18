package com.yowyob.tiibntick.core.roles.adapter.out.kernel;

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

/**
 * Tests for {@link KernelRoleProvisioningAdapter} against a {@link MockWebServer} —
 * focused on {@link KernelRoleProvisioningAdapter#deleteRole(UUID, UUID)}, the
 * {@code DELETE /api/roles/{id}} operation added for Chantier D / Audit n°6 S5.
 *
 * @author MANFOUO Braun
 */
class KernelRoleProvisioningAdapterTest {

    private MockWebServer mockWebServer;
    private KernelRoleProvisioningAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        adapter = new KernelRoleProvisioningAdapter(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("deleteRole sends DELETE /api/roles/{id} with the X-Tenant-Id header and completes on 204")
    void deleteRoleSendsDeleteAndCompletes() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse().setResponseCode(204));

        StepVerifier.create(adapter.deleteRole(tenantId, roleId))
                .verifyComplete();

        RecordedRequest recorded = mockWebServer.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("DELETE");
        assertThat(recorded.getPath()).isEqualTo("/api/roles/" + roleId);
        assertThat(recorded.getHeader("X-Tenant-Id")).isEqualTo(tenantId.toString());
    }

    @Test
    @DisplayName("deleteRole treats a 404 (role already gone) as an idempotent success, not an error")
    void deleteRoleTreatsNotFoundAsNoOp() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(404));

        StepVerifier.create(adapter.deleteRole(UUID.randomUUID(), UUID.randomUUID()))
                .verifyComplete();
    }

    @Test
    @DisplayName("deleteRole propagates other Kernel errors as TntRoleException rather than swallowing them")
    void deleteRolePropagatesOtherErrorsAsDomainException() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        StepVerifier.create(adapter.deleteRole(UUID.randomUUID(), UUID.randomUUID()))
                .expectErrorMatches(e -> e instanceof TntRoleException
                        && ((TntRoleException) e).getCode().equals("ROLE_DELETION_FAILED"))
                .verify();
    }
    @Test
    @DisplayName("Un 401 sur la declaration du vocabulaire n'est PAS cache : la tentative suivante rejoue l'appel")
    void unEchecTransitoireNestPasCache() throws InterruptedException {
        UUID tenantId = UUID.randomUUID();
        // Premiere tentative : le noyau repond 401, comme lorsque la session du compte de service
        // a expire. Rien n'a ete decide sur le vocabulaire lui-meme.
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));
        // Seconde tentative : la session est de nouveau valide, la declaration passe.
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json").setBody("{\"success\":true}"));

        StepVerifier.create(adapter.declareSolutionVocabulary(tenantId))
                .expectError().verify(java.time.Duration.ofSeconds(10));
        StepVerifier.create(adapter.declareSolutionVocabulary(tenantId))
                .expectComplete().verify(java.time.Duration.ofSeconds(10));

        // Deux requetes, et non une : le 401 cache aurait renvoye l'erreur sans rappeler le noyau,
        // condamnant le tenant jusqu'au redemarrage. C'est ce qui bloquait onze entrees de la file.
        assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Un 400 EST cache : un refus de fond ne se rejoue pas")
    void unRefusDeFondResteCache() {
        UUID tenantId = UUID.randomUUID();
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));

        StepVerifier.create(adapter.declareSolutionVocabulary(tenantId))
                .expectError().verify(java.time.Duration.ofSeconds(10));
        StepVerifier.create(adapter.declareSolutionVocabulary(tenantId))
                .expectError().verify(java.time.Duration.ofSeconds(10));

        // Une seule requete : le noyau a tranche sur le vocabulaire, le rejouer ne changerait rien
        // et ferait un appel par role provisionne.
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

}
