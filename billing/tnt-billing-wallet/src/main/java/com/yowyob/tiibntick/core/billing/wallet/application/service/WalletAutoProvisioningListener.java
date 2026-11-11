package com.yowyob.tiibntick.core.billing.wallet.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.session.AuthenticatedSessionListener;
import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IKernelWalletUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Makes sure every TiiBnTick user has a Kernel wallet without having to ask for one.
 *
 * <p>The Kernel creates a wallet only for its authenticated owner ({@code POST
 * /api/payments/wallets/mine}), so this runs at the two moments the Core holds the user's own
 * token: right after the platform gateway relays a login / refresh (the token is passed
 * explicitly, see {@link AuthenticatedSessionListener#BEARER_OVERRIDE_CONTEXT_KEY}) and right
 * after the user's client / freelancer profile is created. Get-or-create is idempotent, and a
 * small per-user cache keeps token refreshes from calling the Kernel every time:
 * a success is remembered for {@code successTtl}, a failure for {@code failureTtl} (so a
 * permanently failing account is retried, but not on every refresh).
 *
 * <p>Failures are never propagated — the login or profile creation must not depend on the wallet.
 *
 * @author MANFOUO Braun
 */
public class WalletAutoProvisioningListener implements AuthenticatedSessionListener {

    private static final Logger log = LoggerFactory.getLogger(WalletAutoProvisioningListener.class);
    private static final int MAX_CACHED_USERS = 20_000;

    private final IKernelWalletUseCase wallets;
    private final ObjectMapper mapper;
    private final Duration successTtl;
    private final Duration failureTtl;
    private final Clock clock;
    /** user id (JWT {@code sub}) -> instant (epoch millis) until which no new check is needed. */
    private final ConcurrentHashMap<String, Long> checkedUntil = new ConcurrentHashMap<>();

    public WalletAutoProvisioningListener(IKernelWalletUseCase wallets, ObjectMapper mapper,
                                          Duration successTtl, Duration failureTtl, Clock clock) {
        this.wallets = wallets;
        this.mapper = mapper;
        this.successTtl = successTtl;
        this.failureTtl = failureTtl;
        this.clock = clock;
    }

    @Override
    public Mono<Void> onTokenIssued(String accessToken) {
        String userId = subjectOf(accessToken);
        long now = clock.millis();
        if (userId != null) {
            Long until = checkedUntil.get(userId);
            if (until != null && until > now) {
                return Mono.empty();
            }
        }
        return ensureWallet(userId)
                .contextWrite(ctx -> ctx.put(BEARER_OVERRIDE_CONTEXT_KEY, accessToken));
    }

    @Override
    public Mono<Void> onProfileCreated() {
        return ensureWallet(null);
    }

    private Mono<Void> ensureWallet(String userIdForCache) {
        return Mono.defer(() -> wallets.getOrCreateMyWallet(null))
                .doOnNext(w -> {
                    remember(userIdForCache, successTtl);
                    log.debug("Kernel wallet ensured for user {} (wallet {})", userIdForCache, w.id());
                })
                .then()
                .onErrorResume(ex -> {
                    remember(userIdForCache, failureTtl);
                    log.warn("Could not ensure the Kernel wallet for user {} (will retry later): {}",
                            userIdForCache, ex.toString());
                    return Mono.empty();
                });
    }

    private void remember(String userId, Duration ttl) {
        if (userId == null) {
            return;
        }
        if (checkedUntil.size() >= MAX_CACHED_USERS) {
            checkedUntil.clear();
        }
        checkedUntil.put(userId, clock.millis() + ttl.toMillis());
    }

    /** The JWT {@code sub} claim, read without validating anything (the token only goes back to the Kernel). */
    private String subjectOf(String accessToken) {
        try {
            String[] parts = accessToken.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            JsonNode claims = mapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
            JsonNode sub = claims.get("sub");
            return sub != null && sub.isTextual() && !sub.asText().isBlank() ? sub.asText() : null;
        } catch (Exception ex) {
            return null;
        }
    }
}
