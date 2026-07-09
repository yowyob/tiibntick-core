package com.yowyob.tiibntick.core.platformgateway.application.service;

import com.yowyob.tiibntick.core.platformgateway.domain.model.ScopeResourceDefinition;

import java.util.List;
import java.util.Set;

/**
 * In-memory registry of known scope "resource" codes — the gateway blocks (Bloc A/B/C)
 * today, plus room for curated business-module proxies as they get built on real demand
 * (see {@code docs/auth/platform-client-management-design.md} §2.6: no proxy is built
 * speculatively — only {@code AUTH}/{@code SSO}/{@code ONBOARDING} are real today).
 *
 * <p>Deliberately a plain list, not a Java enum — adding a new scope resource (e.g. once
 * a {@code DELIVERY} curated proxy is actually built) only means adding an entry here,
 * never touching {@code PermissionMatcher} or the {@code resource:action} scope format.
 *
 * @author MANFOUO Braun
 */
public class PlatformScopeRegistry {

    private static final List<ScopeResourceDefinition> KNOWN_RESOURCES = List.of(
            new ScopeResourceDefinition("AUTH", "Kernel auth-controller/auth-oidc-controller proxy (Bloc A)", Set.of("*")),
            new ScopeResourceDefinition("SSO", "YowYob SSO handshake proxy (Bloc B)", Set.of("*")),
            new ScopeResourceDefinition("ONBOARDING", "Agency onboarding orchestration proxy (Bloc C)", Set.of("*"))
    );

    public List<ScopeResourceDefinition> listAll() {
        return KNOWN_RESOURCES;
    }

    /**
     * A scope string is well-formed if it is the global wildcard ({@code *}) or has the
     * shape {@code RESOURCE:action} where {@code RESOURCE} is a known resource code
     * (case-insensitive).
     */
    public boolean isValidScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return false;
        }
        if (scope.equals("*")) {
            return true;
        }
        int separator = scope.indexOf(':');
        if (separator <= 0 || separator == scope.length() - 1) {
            return false;
        }
        String resource = scope.substring(0, separator);
        return isKnownResource(resource);
    }

    public boolean isKnownResource(String resource) {
        return KNOWN_RESOURCES.stream().anyMatch(def -> def.code().equalsIgnoreCase(resource));
    }
}
