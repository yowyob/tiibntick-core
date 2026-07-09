package com.yowyob.tiibntick.core.platformgateway.domain.model;

/**
 * Deployment environment a {@link PlatformClient} is registered for. A whole client
 * registration belongs to exactly one environment — "Agency/DEV" and "Agency/PROD" are
 * two distinct {@link PlatformClient} rows, each with its own {@code clientId} and
 * independently-managed {@link ApiKey}s (mirrors how Stripe scopes API keys by
 * test/live mode).
 *
 * @author MANFOUO Braun
 */
public enum Environment {
    DEV,
    STAGING,
    PROD
}
