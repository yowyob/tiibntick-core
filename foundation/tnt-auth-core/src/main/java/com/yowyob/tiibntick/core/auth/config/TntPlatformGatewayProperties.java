package com.yowyob.tiibntick.core.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the platform → Core gateway (see root {@code CLAUDE.md}
 * and {@code CORE_KERNEL_GATEWAY_SPEC.md}): the registry of platform backends allowed to
 * call {@code /api/v1/auth/**}/{@code /api/v1/sso/**}, plus SSO app-launch redirect URLs.
 *
 * <p>Deliberately config-driven rather than a new DB-backed table: the set of platforms
 * (Agency, Go, Link, Market, Point Relais, ...) is small and ops-managed, so a YAML/env
 * list avoids introducing R2DBC/Liquibase into tnt-auth-core for a handful of rows. If
 * self-service platform onboarding is ever needed, this can migrate to a persistent
 * adapter behind the same {@code PlatformClientRegistry} port without touching the filter
 * or controllers.
 *
 * <p>Example YAML:
 * <pre>
 * tnt:
 *   auth:
 *     platform-gateway:
 *       clients:
 *         - platform-code: AGENCY
 *           client-id: ${TNT_AGENCY_CLIENT_ID}
 *           api-key: ${TNT_AGENCY_API_KEY}
 *         - platform-code: GO
 *           client-id: ${TNT_GO_CLIENT_ID}
 *           api-key: ${TNT_GO_API_KEY}
 *       sso-app-redirect-urls:
 *         HRM: https://hrm.yowyob.com/sso/callback
 * </pre>
 *
 * @author MANFOUO Braun
 */
@ConfigurationProperties(prefix = "tnt.auth.platform-gateway")
public class TntPlatformGatewayProperties {

    /** Registered platform backends, keyed by {@code X-Client-Id} at lookup time. */
    private List<ClientEntry> clients = new ArrayList<>();

    /**
     * {@code app code -> redirect base URL}, used by the optional
     * {@code POST /api/v1/sso/yowyob/launch} composite endpoint.
     */
    private Map<String, String> ssoAppRedirectUrls = Map.of();

    public List<ClientEntry> getClients() { return clients; }
    public void setClients(List<ClientEntry> clients) { this.clients = clients; }

    public Map<String, String> getSsoAppRedirectUrls() { return ssoAppRedirectUrls; }
    public void setSsoAppRedirectUrls(Map<String, String> ssoAppRedirectUrls) { this.ssoAppRedirectUrls = ssoAppRedirectUrls; }

    public static class ClientEntry {
        private String platformCode;
        private String clientId;
        private String apiKey;
        private boolean enabled = true;

        public String getPlatformCode() { return platformCode; }
        public void setPlatformCode(String platformCode) { this.platformCode = platformCode; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
}
