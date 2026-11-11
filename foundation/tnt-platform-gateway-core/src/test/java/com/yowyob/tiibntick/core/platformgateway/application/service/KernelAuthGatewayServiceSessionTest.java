package com.yowyob.tiibntick.core.platformgateway.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.session.AuthenticatedSessionListener;
import com.yowyob.tiibntick.common.session.AuthenticatedSessionPublisher;
import com.yowyob.tiibntick.core.platformgateway.application.port.out.IKernelAuthGatewayPort;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelApiEnvelope;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelAuthResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("KernelAuthGatewayService — announces issued access tokens to session listeners")
class KernelAuthGatewayServiceSessionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<String> announced = new ArrayList<>();
    private final AuthenticatedSessionListener listener = new AuthenticatedSessionListener() {
        @Override public Mono<Void> onTokenIssued(String t) { announced.add(t); return Mono.empty(); }
        @Override public Mono<Void> onProfileCreated() { return Mono.empty(); }
    };

    private KernelAuthGatewayService service(int status, String dataJson, boolean success) throws Exception {
        IKernelAuthGatewayPort port = mock(IKernelAuthGatewayPort.class);
        JsonNode data = dataJson == null ? null : MAPPER.readTree(dataJson);
        when(port.invokeAuth(any(), anyString(), any(), any())).thenReturn(Mono.just(
                new KernelAuthResult(status, new KernelApiEnvelope(success, data, "m", null, "t"))));
        return new KernelAuthGatewayService(port, new AuthenticatedSessionPublisher(() -> List.of(listener)));
    }

    @Test
    @DisplayName("a successful login answer with an accessToken is announced, and the response is untouched")
    void announcesToken() throws Exception {
        var svc = service(200, "{\"accessToken\":\"jwt-1\",\"refreshToken\":\"r\"}", true);
        StepVerifier.create(svc.callAuth(HttpMethod.POST, "/api/auth/login", null, null))
                .assertNext(r -> assertThat(r.httpStatus()).isEqualTo(200)).verifyComplete();
        assertThat(announced).containsExactly("jwt-1");
    }

    @Test
    @DisplayName("an MFA challenge (202, no accessToken) announces nothing")
    void mfaChallengeIgnored() throws Exception {
        var svc = service(202, "{\"nextStep\":\"CONFIRM_MFA\",\"mfaToken\":\"m\"}", true);
        StepVerifier.create(svc.callAuth(HttpMethod.POST, "/api/auth/login", null, null)).expectNextCount(1).verifyComplete();
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("an error answer or a body without data announces nothing")
    void errorsIgnored() throws Exception {
        StepVerifier.create(service(401, "{\"accessToken\":\"leak\"}", false)
                .callAuth(HttpMethod.POST, "/api/auth/login", null, null)).expectNextCount(1).verifyComplete();
        StepVerifier.create(service(200, null, true)
                .callAuth(HttpMethod.POST, "/api/auth/login", null, null)).expectNextCount(1).verifyComplete();
        assertThat(announced).isEmpty();
    }

    @Test
    @DisplayName("without a publisher (legacy constructor) the service behaves as a plain proxy")
    void worksWithoutPublisher() throws Exception {
        IKernelAuthGatewayPort port = mock(IKernelAuthGatewayPort.class);
        when(port.invokeAuth(any(), anyString(), any(), any())).thenReturn(Mono.just(
                new KernelAuthResult(200, new KernelApiEnvelope(true, MAPPER.readTree("{\"accessToken\":\"x\"}"), "m", null, "t"))));
        StepVerifier.create(new KernelAuthGatewayService(port).callAuth(HttpMethod.POST, "/p", null, null))
                .expectNextCount(1).verifyComplete();
    }
}
