package com.yowyob.tiibntick.core.roles.application.service;

import com.yowyob.tiibntick.core.roles.application.port.out.ReactivePermissionResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Goes through the REAL {@link TntRoleDefinitionRegistry} (built from the {@code TntRole} enum) with a JWT that only
 * carries {@code ROLE_FREELANCER} / {@code ROLE_CLIENT} — exactly what a livreur or a client sends. Regression for
 * "Access denied: permission 'invoice:read' / 'report:own' is required" on the freelancer billing screens: the
 * permissions had been declared on the controllers but never granted by the role definition the evaluator reads.
 */
class TntProfileRolePermissionsTest {

    private final ReactivePermissionResolver kernelResolver = mock(ReactivePermissionResolver.class);
    private final TntPermissionEvaluator evaluator =
            new TntPermissionEvaluator(kernelResolver, new TntRoleDefinitionRegistry());

    private Mono<Boolean> check(String role, String resource, String action) {
        var auth = new UsernamePasswordAuthenticationToken(
                "user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return evaluator.canFromCurrentContext(resource, action)
                .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl(auth))));
    }

    /** Every guard hit by the livreur screens: invoices & revenues, wallet, payments, profile. */
    @ParameterizedTest(name = "FREELANCER may {0}:{1}")
    @CsvSource({
            "invoice,read", "report,own", "wallet,read", "wallet,write", "payment,process", "freelancer,read",
    })
    @DisplayName("a FREELANCER JWT alone grants every permission of the livreur billing / wallet screens")
    void freelancerHasWhatItsScreensNeed(String resource, String action) {
        StepVerifier.create(check("FREELANCER", resource, action)).expectNext(true).verifyComplete();
        verifyNoInteractions(kernelResolver);
    }

    /** Every guard hit by the client screens: invoices, wallet, payments, delivery payment. */
    @ParameterizedTest(name = "CLIENT may {0}:{1}")
    @CsvSource({
            "invoice,read", "wallet,read", "wallet,write", "payment,process", "billing,read",
    })
    @DisplayName("a CLIENT JWT alone grants every permission of the client billing / wallet screens")
    void clientHasWhatItsScreensNeed(String resource, String action) {
        StepVerifier.create(check("CLIENT", resource, action)).expectNext(true).verifyComplete();
        verifyNoInteractions(kernelResolver);
    }

    /** The grant stays minimal: a driver does not get the management-wide reports or invoice issuing. */
    @ParameterizedTest(name = "FREELANCER may NOT {0}:{1}")
    @CsvSource({"report,read", "report,export", "invoice,issue", "invoice,cancel", "billing,write"})
    void freelancerStaysRestricted(String resource, String action) {
        StepVerifier.create(check("FREELANCER", resource, action)).expectNext(false).verifyComplete();
    }

    @ParameterizedTest(name = "CLIENT may NOT {0}:{1}")
    @CsvSource({"report,own", "report,read", "invoice,issue", "invoice,cancel"})
    void clientStaysRestricted(String resource, String action) {
        StepVerifier.create(check("CLIENT", resource, action)).expectNext(false).verifyComplete();
    }
}
