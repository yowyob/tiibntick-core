package com.yowyob.tiibntick.common.aop;

import com.yowyob.tiibntick.common.annotation.TenantScoped;
import com.yowyob.tiibntick.common.tenant.CurrentTenantUseCase;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantValidationAspect}'s blocking (non-reactive) branch
 * (Chantier D · Audit n°6 · S14 / Audit n°1 · A3): {@code .block()} used to have no
 * timeout, so a tenant lookup that never completed would hang the calling thread
 * forever — potentially a Netty event-loop thread, since the aspect has no way to know
 * its caller's threading context ahead of time.
 *
 * <p>No {@code @TenantScoped} method in the codebase is non-reactive today (verified via
 * repo-wide search), so this branch is currently unreachable in production — this test
 * exercises it directly to guarantee it stays safe for whenever one is added.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantValidationAspect — blocking branch timeout (S14/A3)")
class TenantValidationAspectTest {

    @Mock
    private CurrentTenantUseCase currentTenantUseCase;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature methodSignature;
    @Mock
    private TenantScoped tenantScoped;

    private TenantValidationAspect aspect;

    /** Stand-in for a hypothetical non-reactive @TenantScoped method. */
    static class BlockingTarget {
        public String doWork(final UUID tenantId) {
            return "ok";
        }
    }

    @BeforeEach
    void setUp() {
        aspect = new TenantValidationAspect(currentTenantUseCase);
    }

    @Test
    @DisplayName("a tenant lookup that never completes times out instead of hanging forever")
    void blockingBranchTimesOutRatherThanHangingForever() throws Throwable {
        final Method method = BlockingTarget.class.getMethod("doWork", UUID.class);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        // Simulates a hung/never-populated Reactor Context — the exact failure mode the
        // missing timeout used to leave completely unbounded. Never emitting means
        // validateTenant()'s .map(...) callback (which would call joinPoint.getArgs()) is
        // never reached — only the timeout path is under test here.
        when(currentTenantUseCase.currentTenant()).thenReturn(Mono.never());

        final long start = System.nanoTime();

        assertThatThrownBy(() -> aspect.validateTenant(joinPoint, tenantScoped))
                .as("Mono#block(Duration) surfaces a timeout as IllegalStateException")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Timeout");

        final Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
        assertThat(elapsed)
                .as("must be bounded by the aspect's own timeout, not hang indefinitely")
                .isLessThan(Duration.ofSeconds(10));
    }
}
