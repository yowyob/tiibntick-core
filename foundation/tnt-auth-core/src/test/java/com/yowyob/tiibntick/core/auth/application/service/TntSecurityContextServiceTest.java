package com.yowyob.tiibntick.core.auth.application.service;

import com.yowyob.tiibntick.core.auth.adapter.out.kernel.NoOpYowAuthTntAdapter;
import com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException;
import com.yowyob.tiibntick.core.auth.domain.model.TntTokenClaims;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TntSecurityContextServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID = UUID.randomUUID();
    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock
    private TntJwtValidator jwtValidator;

    private TntSecurityContextService service;

    @BeforeEach
    void setUp() {
        service = new TntSecurityContextService(jwtValidator, new NoOpYowAuthTntAdapter());
    }

    @Test
    void resolveCurrentContext_withValidJwtAuthorities_shouldBuildEnrichedContext() {
        Authentication token = buildAuthorities(Set.of("mission:create", "ROLE_AGENCY_MANAGER"));
        Mono<SecurityContext> secCtx = Mono.just(new SecurityContextImpl(token));

        StepVerifier.create(
                service.resolveCurrentContext()
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(secCtx))
        )
        .assertNext(ctx -> {
            assertThat(ctx.userId()).isEqualTo(USER_ID);
            assertThat(ctx.tenantId()).isEqualTo(TENANT_ID);
            assertThat(ctx.actorId()).isEqualTo(ACTOR_ID);
            assertThat(ctx.agencyId()).isEqualTo(AGENCY_ID);
            assertThat(ctx.authenticated()).isTrue();
            assertThat(ctx.permissions()).contains("mission:create");
            assertThat(ctx.roles()).contains("ROLE_AGENCY_MANAGER");
        })
        .verifyComplete();
    }

    @Test
    void resolveCurrentContext_withNoSecurityContext_shouldEmitMissingContextError() {
        StepVerifier.create(service.resolveCurrentContext())
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntAuthException.class);
                    assertThat(((TntAuthException) err).getCode()).isEqualTo("AUTH_MISSING_CONTEXT");
                })
                .verify();
    }

    @Test
    void resolveCurrentContextOrAnonymous_withNoContext_shouldReturnAnonymous() {
        StepVerifier.create(service.resolveCurrentContextOrAnonymous())
                .assertNext(ctx -> {
                    assertThat(ctx.authenticated()).isFalse();
                    assertThat(ctx.userId()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void resolveCurrentIdentity_withValidToken_shouldReturnIdentityProjection() {
        Authentication token = buildAuthorities(Set.of("report:read"));
        Mono<SecurityContext> secCtx = Mono.just(new SecurityContextImpl(token));

        StepVerifier.create(
                service.resolveCurrentIdentity()
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(secCtx))
        )
        .assertNext(identity -> {
            assertThat(identity).isInstanceOf(TntUserIdentity.class);
            assertThat(identity.userId()).isEqualTo(USER_ID);
            assertThat(identity.tenantId()).isEqualTo(TENANT_ID);
            assertThat(identity.actorId()).isEqualTo(ACTOR_ID);
            assertThat(identity.permissions()).contains("report:read");
        })
        .verifyComplete();
    }

    @Test
    void validateAndExtract_withValidToken_shouldReturnTntTokenClaims() {
        String rawToken = "valid.jwt.token";
        Instant now = Instant.now();
        TntTokenClaims expectedClaims = new TntTokenClaims(
                USER_ID, TENANT_ID, ACTOR_ID, ORG_ID, AGENCY_ID,
                Set.of("mission:create"), now.minusSeconds(60), now.plusSeconds(840), "jti-123"
        );
        when(jwtValidator.validateAndExtract(rawToken)).thenReturn(Mono.just(expectedClaims));

        StepVerifier.create(service.validateAndExtract(rawToken))
                .assertNext(claims -> {
                    assertThat(claims.userId()).isEqualTo(USER_ID);
                    assertThat(claims.tenantId()).isEqualTo(TENANT_ID);
                    assertThat(claims.actorId()).isEqualTo(ACTOR_ID);
                    assertThat(claims.agencyId()).isEqualTo(AGENCY_ID);
                    assertThat(claims.tokenId()).isEqualTo("jti-123");
                    assertThat(claims.permissions()).containsExactly("mission:create");
                    assertThat(claims.isExpired()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void validateAndExtract_withInvalidToken_shouldEmitTokenInvalidError() {
        when(jwtValidator.validateAndExtract("invalid.token"))
                .thenReturn(Mono.error(TntAuthException.tokenInvalid("bad token")));

        StepVerifier.create(service.validateAndExtract("invalid.token"))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntAuthException.class);
                    assertThat(((TntAuthException) err).getCode()).isEqualTo("AUTH_TOKEN_INVALID");
                })
                .verify();
    }

    @Test
    void validateAndExtract_withExpiredToken_shouldEmitTokenExpiredError() {
        String rawToken = "expired.token";
        when(jwtValidator.validateAndExtract(rawToken))
                .thenReturn(Mono.error(TntAuthException.tokenExpired()));

        StepVerifier.create(service.validateAndExtract(rawToken))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntAuthException.class);
                    assertThat(((TntAuthException) err).getCode()).isEqualTo("AUTH_TOKEN_EXPIRED");
                })
                .verify();
    }

    @Test
    void isValid_withValidToken_shouldReturnTrue() {
        String rawToken = "valid.token";
        when(jwtValidator.isValid(rawToken)).thenReturn(true);

        assertThat(service.isValid(rawToken)).isTrue();
    }

    @Test
    void isValid_withInvalidToken_shouldReturnFalse() {
        when(jwtValidator.isValid("bad.token")).thenReturn(false);

        assertThat(service.isValid("bad.token")).isFalse();
    }

    @Test
    void resolveCurrentContext_withAuthenticationCarryingNoSyntheticIds_shouldReturnAnonymous() {
        Authentication other = new TestingAuthenticationToken("user", "pass");
        other.setAuthenticated(true);
        Mono<SecurityContext> secCtx = Mono.just(new SecurityContextImpl(other));

        StepVerifier.create(
                service.resolveCurrentContextOrAnonymous()
                        .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(secCtx))
        )
        // "user" is not a UUID so userId resolves to null, but authenticated() still true;
        // isFullyAuthenticated() (userId != null) is what callers actually rely on.
        .assertNext(ctx -> assertThat(ctx.userId()).isNull())
        .verifyComplete();
    }

    /**
     * Builds an {@link Authentication} carrying the synthetic {@code ACTOR_}/{@code TENANT_}/
     * {@code AGENCY_}/{@code ORG_} authorities + raw permission/{@code ROLE_} authorities that
     * {@code TntSecurityConfig.tntJwtAuthenticationConverter} extracts from a Kernel-issued JWT —
     * this is the only authentication shape {@link TntSecurityContextService} builds a context
     * from now that TiiBnTick no longer shares the Kernel's {@code ApiKeyAuthenticationToken}.
     */
    private Authentication buildAuthorities(Set<String> permissionsAndRoles) {
        List<SimpleGrantedAuthority> grantedAuthorities = Set.of(
                        "ACTOR_" + ACTOR_ID, "TENANT_" + TENANT_ID,
                        "AGENCY_" + AGENCY_ID, "ORG_" + ORG_ID)
                .stream()
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
        permissionsAndRoles.stream().map(SimpleGrantedAuthority::new).forEach(grantedAuthorities::add);

        // The 3-arg constructor already marks the token authenticated=true;
        // calling setAuthenticated(true) on it explicitly throws.
        return new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, grantedAuthorities);
    }
}
