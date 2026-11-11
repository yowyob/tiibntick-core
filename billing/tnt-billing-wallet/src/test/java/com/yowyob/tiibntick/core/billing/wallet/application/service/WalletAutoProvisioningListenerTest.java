package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.session.AuthenticatedSessionListener;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IKernelWalletUseCase;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.kernel.KernelWalletDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WalletAutoProvisioningListener — every session ends up with a Kernel wallet")
class WalletAutoProvisioningListenerTest {

    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> bearerSeen = new AtomicReference<>();
    private volatile boolean failing;
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-11T10:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId z) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    /** Stands in for KernelWalletService: records the explicit-bearer context key the Kernel call would relay. */
    private final IKernelWalletUseCase wallets = mockWallets();

    private IKernelWalletUseCase mockWallets() {
        IKernelWalletUseCase mock = org.mockito.Mockito.mock(IKernelWalletUseCase.class);
        org.mockito.Mockito.when(mock.getOrCreateMyWallet(org.mockito.ArgumentMatchers.any())).thenAnswer(inv ->
                Mono.deferContextual(ctx -> {
                    calls.incrementAndGet();
                    bearerSeen.set(ctx.getOrDefault(AuthenticatedSessionListener.BEARER_OVERRIDE_CONTEXT_KEY, null));
                    if (failing) {
                        return Mono.error(new IllegalStateException("Kernel 503"));
                    }
                    return Mono.just(new KernelWalletDto(UUID.randomUUID(), UUID.randomUUID(), "USER", "n",
                            BigDecimal.ZERO, null, null));
                }));
        return mock;
    }

    private final WalletAutoProvisioningListener listener = new WalletAutoProvisioningListener(
            wallets, new ObjectMapper(), Duration.ofHours(1), Duration.ofMinutes(5), clock);

    private static String jwtFor(String sub) {
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(("{\"sub\":\"" + sub + "\"}").getBytes());
        return "h." + payload + ".s";
    }

    @Test
    @DisplayName("a login token creates/finds the wallet and the Kernel call carries THAT token")
    void tokenIssuedEnsuresWalletWithThatToken() {
        String token = jwtFor("user-1");
        StepVerifier.create(listener.onTokenIssued(token)).verifyComplete();
        assertThat(calls).hasValue(1);
        assertThat(bearerSeen.get()).isEqualTo(token);
    }

    @Test
    @DisplayName("token refreshes of the same user do not hit the Kernel again within the TTL, then do")
    void cachePerUser() {
        StepVerifier.create(listener.onTokenIssued(jwtFor("user-1"))).verifyComplete();
        StepVerifier.create(listener.onTokenIssued(jwtFor("user-1"))).verifyComplete();
        assertThat(calls).hasValue(1);

        StepVerifier.create(listener.onTokenIssued(jwtFor("user-2"))).verifyComplete();
        assertThat(calls).hasValue(2);

        now.set(now.get().plus(Duration.ofHours(2)));
        StepVerifier.create(listener.onTokenIssued(jwtFor("user-1"))).verifyComplete();
        assertThat(calls).hasValue(3);
    }

    @Test
    @DisplayName("a Kernel failure is swallowed, retried after the short failure TTL, never before")
    void failureSwallowedAndRetriedLater() {
        failing = true;
        StepVerifier.create(listener.onTokenIssued(jwtFor("user-1"))).verifyComplete();
        StepVerifier.create(listener.onTokenIssued(jwtFor("user-1"))).verifyComplete();
        assertThat(calls).hasValue(1);

        failing = false;
        now.set(now.get().plus(Duration.ofMinutes(6)));
        StepVerifier.create(listener.onTokenIssued(jwtFor("user-1"))).verifyComplete();
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("an unparsable token still triggers the check (no cache key), without failing")
    void opaqueToken() {
        StepVerifier.create(listener.onTokenIssued("opaque")).verifyComplete();
        StepVerifier.create(listener.onTokenIssued("opaque")).verifyComplete();
        assertThat(calls).hasValue(2);
    }

    @Test
    @DisplayName("profile creation ensures the wallet in the caller's own context (no bearer override)")
    void profileCreated() {
        StepVerifier.create(listener.onProfileCreated()).verifyComplete();
        assertThat(calls).hasValue(1);
        assertThat(bearerSeen.get()).isNull();
    }
}
