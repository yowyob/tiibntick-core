package com.yowyob.tiibntick.bootstrap.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the Kernel puts in the JWT after a profile is created becomes usable roles — but only for
 * the self-service personas, never for privileged codes a tenant owner could mint for themselves.
 */
class TntJwtSelfServiceRolesTest {

    @Test
    @DisplayName("ROLE_FREELANCER and a scoped ROLE_CLIENT#… become plain ROLE_ authorities")
    void selfServiceRolesAreMapped() {
        assertThat(TntSecurityConfig.selfServiceRoleAuthority("ROLE_FREELANCER")).contains("ROLE_FREELANCER");
        assertThat(TntSecurityConfig.selfServiceRoleAuthority("ROLE_CLIENT#TENANT:abc")).contains("ROLE_CLIENT");
    }

    @Test
    @DisplayName("privileged and unrelated entries are never derived from the token")
    void privilegedRolesAreNeverDerived() {
        for (String p : List.of("ROLE_TNT_ADMIN", "ROLE_AGENCY_MANAGER", "ROLE_OWNER", "tenant:admin", "wallet:read", "CLIENT")) {
            assertThat(TntSecurityConfig.selfServiceRoleAuthority(p)).isEqualTo(Optional.empty());
        }
        assertThat(TntSecurityConfig.selfServiceRoleAuthority(null)).isEmpty();
    }

    @Test
    @DisplayName("the real converter exposes ROLE_FREELANCER for hasRole checks and keeps ROLE_TNT_ADMIN out")
    void converterEndToEnd() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("tid", "11111111-1111-1111-1111-111111111111")
                .claim("permissions", List.of("ROLE_OWNER", "ROLE_FREELANCER#TENANT:x", "ROLE_TNT_ADMIN"))
                .build();
        TntSecurityConfig config = new TntSecurityConfig();
        org.springframework.test.util.ReflectionTestUtils.setField(config, "systemTenantId",
                "00000000-0000-0000-0000-000000000001");
        ReactiveJwtAuthenticationConverter converter = config.tntJwtAuthenticationConverter();

        Set<String> authorities = converter.convert(jwt).block().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertThat(authorities).contains("ROLE_FREELANCER");
        // the raw claim entry is kept verbatim (permissions evaluator), but the privileged role
        // is only a raw string, not a hasRole authority derived by the new mapping
        assertThat(authorities).doesNotContain("ROLE_CLIENT");
    }
}
