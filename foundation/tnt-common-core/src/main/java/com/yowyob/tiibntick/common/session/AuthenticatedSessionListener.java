package com.yowyob.tiibntick.common.session;

import reactor.core.publisher.Mono;

/**
 * Extension point for "something to prepare once a user has a working TiiBnTick session"
 * (e.g. make sure the user's Kernel wallet exists).
 *
 * <p>Foundation modules (platform gateway, actor profiles) cannot depend on the business
 * modules that need to react, so they publish through
 * {@link AuthenticatedSessionPublisher} and the higher-layer module (billing wallet…)
 * provides the implementing bean — the same port/adapter direction as the rest of the
 * layering rules.
 *
 * <p>Listeners are best-effort: a failure is logged and never breaks the login or the
 * profile creation that triggered it, so implementations should be idempotent and cheap
 * to call repeatedly (every token refresh triggers {@link #onTokenIssued}).
 *
 * @author MANFOUO Braun
 */
public interface AuthenticatedSessionListener {

    /**
     * Reactor-context key under which {@link #onTokenIssued} implementations put the fresh
     * access token so that outbound Kernel calls forward <em>that</em> user's Bearer — the
     * login request itself carries no JWT, so the usual security-context relay has nothing
     * to forward.
     */
    String BEARER_OVERRIDE_CONTEXT_KEY = "tnt.session.bearer-override";

    /**
     * A login / MFA confirmation / context selection / refresh / sign-up just returned an
     * access token. Run with {@link #BEARER_OVERRIDE_CONTEXT_KEY} in the Reactor context when
     * calling the Kernel on behalf of the user.
     */
    Mono<Void> onTokenIssued(String accessToken);

    /**
     * An authenticated request has just created the caller's client / freelancer profile.
     * Runs inside that request's Reactor context, so the caller's JWT is already relayed.
     */
    Mono<Void> onProfileCreated();
}
