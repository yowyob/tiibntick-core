package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.platformgateway.application.port.in.ProxyKernelAuthUseCase;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelApiEnvelope;
import com.yowyob.tiibntick.core.platformgateway.domain.model.KernelAuthResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@code POST /api/v1/auth/logout} must forward to the Kernel's {@code POST /api/auth/logout}
 * with the caller's bearer token and the optional refresh-token body, and relay the Kernel status.
 *
 * @author MANFOUO Braun
 */
class PlatformAuthControllerLogoutTest {

    private final ProxyKernelAuthUseCase useCase = mock(ProxyKernelAuthUseCase.class);
    private final PlatformAuthController controller = new PlatformAuthController(useCase);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void forwardsBearerAndRefreshTokenToKernelLogout() {
        JsonNode body = mapper.createObjectNode().put("refreshToken", "r-123");
        when(useCase.callAuth(eq(HttpMethod.POST), eq("/api/auth/logout"), any(), eq("Bearer access-1")))
                .thenReturn(Mono.just(new KernelAuthResult(200, new KernelApiEnvelope(true, null, "ok", null, null))));

        var response = controller.logout(body, "Bearer access-1").block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().isSuccess()).isTrue();
        verify(useCase).callAuth(HttpMethod.POST, "/api/auth/logout", body, "Bearer access-1");
    }

    @Test
    void relaysKernelUnauthorizedStatusWithoutBody() {
        when(useCase.callAuth(eq(HttpMethod.POST), eq("/api/auth/logout"), eq(null), eq(null)))
                .thenReturn(Mono.just(new KernelAuthResult(401,
                        new KernelApiEnvelope(false, null, "unauthorized", "UNAUTHORIZED", null))));

        var response = controller.logout(null, null).block();

        assertThat(response).isNotNull();
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().isSuccess()).isFalse();
    }
}
