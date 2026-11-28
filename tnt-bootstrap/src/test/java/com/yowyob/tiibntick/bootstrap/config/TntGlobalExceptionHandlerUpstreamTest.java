package com.yowyob.tiibntick.bootstrap.config;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class TntGlobalExceptionHandlerUpstreamTest {

    private final TntGlobalExceptionHandler handler = new TntGlobalExceptionHandler();

    @Test
    void aKernelRefusalIsABadGateway_neverForwardedAsAnAuthenticationFailure() {
        var kernel403 = WebClientResponseException.create(403, "Forbidden", HttpHeaders.EMPTY,
                "{\"errorCode\":\"CLIENT_APPLICATION_SERVICE_NOT_ALLOWED\"}".getBytes(StandardCharsets.UTF_8), null);
        StepVerifier.create(handler.handleKernelResponse(kernel403)).assertNext(pd -> {
            assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
            assertThat(pd.getDetail()).contains("403").contains("CLIENT_APPLICATION_SERVICE_NOT_ALLOWED");
            assertThat(pd.getProperties()).containsEntry("upstreamStatus", 403);
        }).verifyComplete();
    }

    @Test
    void aLongKernelBodyIsTruncated() {
        var kernel500 = WebClientResponseException.create(500, "Server Error", HttpHeaders.EMPTY,
                "x".repeat(5000).getBytes(StandardCharsets.UTF_8), null);
        StepVerifier.create(handler.handleKernelResponse(kernel500))
                .assertNext(pd -> assertThat(pd.getDetail().length()).isLessThan(400)).verifyComplete();
    }

    @Test
    void aTimeoutIsAGatewayTimeoutAndAnOpenCircuitIsServiceUnavailable() {
        StepVerifier.create(handler.handleKernelTimeout(new TimeoutException("5s")))
                .assertNext(pd -> assertThat(pd.getStatus()).isEqualTo(504)).verifyComplete();
        var open = CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("t"));
        StepVerifier.create(handler.handleKernelCircuitOpen(open))
                .assertNext(pd -> assertThat(pd.getStatus()).isEqualTo(503)).verifyComplete();
    }
}
