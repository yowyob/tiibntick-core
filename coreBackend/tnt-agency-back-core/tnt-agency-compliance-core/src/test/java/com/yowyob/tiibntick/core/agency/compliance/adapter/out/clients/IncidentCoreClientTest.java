package com.yowyob.tiibntick.core.agency.compliance.adapter.out.clients;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.agency.compliance.application.port.out.IncidentCorePort;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L3: the agency declares the deliverer's anomaly with the incident contract of the core (role, types a courier
 * may declare), over HTTP against a stub of the core.
 */
class IncidentCoreClientTest {

    private MockWebServer core;
    private IncidentCoreClient client;

    @BeforeEach
    void start() throws Exception {
        core = new MockWebServer();
        core.start();
        client = new IncidentCoreClient(WebClient.builder().baseUrl(core.url("/").toString()).build());
    }

    @AfterEach
    void stop() throws Exception {
        core.shutdown();
    }

    private IncidentCorePort.ReportIncidentRequest request(String anomaly, UUID deliverer) {
        return new IncidentCorePort.ReportIncidentRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                anomaly, "panne", deliverer);
    }

    @Test
    @DisplayName("an accident is declared by the deliverer as AGENCY_DRIVER with a declarable type")
    void declaredWithTheCoreContract() throws Exception {
        UUID incident = UUID.randomUUID();
        core.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"" + incident + "\",\"status\":\"TRIAGED\",\"referenceCode\":\"TNT-INC-261126-0A1B2C3D4E\"}"));
        UUID deliverer = UUID.randomUUID();

        IncidentCorePort.IncidentView view = client.reportIncident(request("accident", deliverer)).block();

        assertThat(view.id()).isEqualTo(incident);
        assertThat(view.status()).isEqualTo("TRIAGED");
        RecordedRequest sent = core.takeRequest(5, TimeUnit.SECONDS);
        assertThat(sent.getPath()).isEqualTo("/api/v1/incidents");
        JsonNode body = new ObjectMapper().readTree(sent.getBody().readUtf8());
        assertThat(body.path("reportedByRole").asText()).isEqualTo("AGENCY_DRIVER");
        assertThat(body.path("type").asText()).isEqualTo("DRIVER_ACCIDENT_PHYSICAL");
        assertThat(body.path("reportedByActorId").asText()).isEqualTo(deliverer.toString());
    }

    @Test
    @DisplayName("a delay, an unknown anomaly or a missing deliverer send nothing to the core")
    void notDeclared() {
        assertThat(client.reportIncident(request("DELAY", UUID.randomUUID())).block()).isNull();
        assertThat(client.reportIncident(request("SOMETHING", UUID.randomUUID())).block()).isNull();
        assertThat(client.reportIncident(request("ACCIDENT", null)).block()).isNull();
        assertThat(core.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("QO-12: never a type the courier may not declare")
    void typesAreDeclarable() {
        assertThat(IncidentCoreClient.mapIncidentType("THEFT")).isEqualTo("PARCEL_TOTAL_LOSS");
        assertThat(IncidentCoreClient.mapIncidentType("GPS")).isEqualTo("DRIVER_GPS_LOSS");
        assertThat(IncidentCoreClient.mapIncidentType("CLIENT_REFUSAL")).isEqualTo("CLIENT_REFUSED_RECEPTION");
        assertThat(IncidentCoreClient.mapIncidentType("DAMAGE")).isEqualTo("PARCEL_PHYSICALLY_DAMAGED");
        assertThat(IncidentCoreClient.mapIncidentType(null)).isNull();
    }
}
