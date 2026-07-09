package com.yowyob.tiibntick.bootstrap.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Swagger UI configuration aggregating all module REST endpoints.
 *
 * <p> — Changes from v2.1:
 * <ul>
 *   <li>Added {@link #incidentCoreApi()} group for {@code tnt-incident-core} endpoints.</li>
 *   <li>Added {@link #administrationCoreApi()} group for {@code tnt-administration-core}
 *       (RBAC management).</li>
 *   <li>Updated JWT Bearer scheme description to include the full TiiBnTick RBAC model
 *       ({@code TntRole} enum + permission format).</li>
 *   <li>Added {@code X-Tenant-Id} header as a global parameter description.</li>
 * </ul>
 *
 * <p>Uses {@link TntApiInfo} for centralized API metadata.
 *
 * @author MANFOUO Braun
 */
@Configuration
public class TntOpenApiConfig {

    @Value("${tnt.swagger.server-url:http://localhost:8080}")
    private String serverUrl;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:http://localhost:9000}")
    private String issuerUri;

    @Bean
    public OpenAPI tntCoreOpenApi() {
        TntApiInfo apiInfo = TntApiInfo.defaults();
        return new OpenAPI()
                .info(new Info()
                        .title(apiInfo.getTitle())
                        .description(apiInfo.getDescription())
                        .version(apiInfo.getVersion())
                        .contact(new Contact()
                                .name(apiInfo.getContactName())
                                .email(apiInfo.getContactEmail())
                                .url("https://gitlab.com/tiibntick-org"))
                        .license(new License()
                                .name(apiInfo.getLicenseName())
                                .url("https://tiibntick.yowyob.com/license")))
                .servers(List.of(
                        new Server().url(serverUrl).description("Current environment"),
                        new Server().url("http://localhost:8080").description("Local development")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description(buildBearerSchemeDescription()))
                        .addSecuritySchemes("oauth2", new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl(issuerUri + "/oauth2/authorize")
                                        .tokenUrl(issuerUri + "/oauth2/token"))))
                        // Platform-gateway credentials (tnt-platform-gateway-core) — X-Client-Id/X-Api-Key,
                        // NOT a JWT. Applied per-operation on PlatformAuthController/PlatformAuthOidcController/
                        // PlatformSsoController via @SecurityRequirement, which overrides the global bearerAuth
                        // requirement below for those operations only — see docs/auth/platform-client-management-design.md.
                        .addSecuritySchemes("ClientIdAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Client-Id")
                                .description("Platform backend Client-ID (public identifier, issued by a TNT_ADMIN via POST /api/v1/admin/platform-clients)"))
                        .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Api-Key")
                                .description("Platform backend API Key secret — shown once at issuance/rotation, format tnt_<base64>")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    // ── Grouped APIs — one tab per domain in Swagger UI ───────────────────────

    @Bean
    public GroupedOpenApi allEndpointsApi() {
        return GroupedOpenApi.builder()
                .group("00-all")
                .displayName("All Endpoints")
                .pathsToMatch("/api/**")
                .build();
    }

    /**
     * Platform Gateway group: platform backend authentication proxy (Bloc A/B, X-Client-Id/
     * X-Api-Key) plus the TNT_ADMIN-only platform-client management API.
     */
    @Bean
    public GroupedOpenApi platformGatewayApi() {
        return GroupedOpenApi.builder()
                .group("01-platform-gateway")
                .displayName("L1 — Platform Gateway (Client-ID/API-Key, Admin)")
                .pathsToMatch("/api/v1/auth/**", "/api/v1/sso/**",
                              "/api/v1/admin/platform-clients/**", "/api/v1/admin/api-keys/**",
                              "/api/v1/admin/scope-registry/**")
                .build();
    }

    @Bean
    public GroupedOpenApi actorCoreApi() {
        return GroupedOpenApi.builder()
                .group("02-actor-core")
                .displayName("L2 — Actor Core (Deliverers, Freelancers, GPS)")
                .pathsToMatch("/api/v1/actors/**", "/api/v1/deliverers/**", "/api/v1/freelancers/**")
                .build();
    }

    @Bean
    public GroupedOpenApi organizationCoreApi() {
        return GroupedOpenApi.builder()
                .group("02-organization-core")
                .displayName("L2 — Organization Core (Agency, Branch, Hub, Zone)")
                .pathsToMatch("/api/v1/organizations/**", "/api/v1/agencies/**",
                              "/api/v1/branches/**", "/api/v1/hubs/**")
                .build();
    }

    /**
     *  — Administration Core group: RBAC management, role assignments, tenant provisioning.
     * Endpoints managed by {@code tnt-administration-core} using roles from {@code tnt-roles-core}.
     */
    @Bean
    public GroupedOpenApi administrationCoreApi() {
        return GroupedOpenApi.builder()
                .group("02-administration-core")
                .displayName("L2 — Administration Core (RBAC, Roles, Tenant Provisioning)")
                .pathsToMatch("/api/v1/admin/**", "/api/v1/roles/**", "/api/v1/permissions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi deliveryCoreApi() {
        return GroupedOpenApi.builder()
                .group("03-delivery-core")
                .displayName("L3 — Delivery Core (Mission, Package, Hub Deposit)")
                .pathsToMatch("/api/v1/missions/**", "/api/v1/packages/**",
                              "/api/v1/hub-deposits/**", "/api/v1/tracking/**")
                .build();
    }

    /**
     *  — Incident Core group: incident reporting, triage, auto-resolution,
     * inter-agency cooperation, blockchain evidence chain.
     * Maps to all {@code /api/v1/incidents/**} endpoints provided by {@code tnt-incident-core}.
     */
    @Bean
    public GroupedOpenApi incidentCoreApi() {
        return GroupedOpenApi.builder()
                .group("03-incident-core")
                .displayName("L3 — Incident Core (Reports, Triage, Auto-Resolution, Blockchain Evidence)")
                .pathsToMatch("/api/v1/incidents/**")
                .build();
    }

    @Bean
    public GroupedOpenApi disputeCoreApi() {
        return GroupedOpenApi.builder()
                .group("03-dispute-core")
                .displayName("L3 — Dispute Core (Litiges, Evidence, Remboursements)")
                .pathsToMatch("/api/v1/disputes/**")
                .build();
    }

    @Bean
    public GroupedOpenApi geoCoreApi() {
        return GroupedOpenApi.builder()
                .group("03-geo-core")
                .displayName("L3 — Geo Core (Geocoding, POI, Graph)")
                .pathsToMatch("/api/v1/geo/**", "/api/v1/locations/**", "/api/v1/poi/**")
                .build();
    }

    @Bean
    public GroupedOpenApi routeCoreApi() {
        return GroupedOpenApi.builder()
                .group("03-route-core")
                .displayName("L3 — Route Core (VRP, A*, ETA, Kalman)")
                .pathsToMatch("/api/v1/routes/**", "/api/v1/vrp/**", "/api/v1/eta/**")
                .build();
    }

    @Bean
    public GroupedOpenApi mediaCoreApi() {
        return GroupedOpenApi.builder()
                .group("03-media-core")
                .displayName("L3 — Media Core (QR Code, PDF, MinIO)")
                .pathsToMatch("/api/v1/media/**", "/api/v1/qr/**", "/api/v1/documents/**")
                .build();
    }

    @Bean
    public GroupedOpenApi notifyCoreApi() {
        return GroupedOpenApi.builder()
                .group("03-notify-core")
                .displayName("L3 — Notify Core (FCM, SMS MTN/Orange, WhatsApp)")
                .pathsToMatch("/api/v1/notifications/**")
                .build();
    }

    @Bean
    public GroupedOpenApi staffFleetApi() {
        return GroupedOpenApi.builder()
                .group("04-staff-fleet")
                .displayName("L4 — Staff & Fleet (Resources, Vehicles, Equipment)")
                .pathsToMatch("/api/v1/vehicles/**", "/api/v1/equipment/**",
                              "/api/v1/resources/**")
                .build();
    }

    @Bean
    public GroupedOpenApi realtimeSyncApi() {
        return GroupedOpenApi.builder()
                .group("03-realtime-sync")
                .displayName("L3 — Realtime & Sync (WebSocket, GPS, Offline)")
                .pathsToMatch("/api/v1/realtime/**", "/api/v1/sync/**", "/ws/**")
                .build();
    }

    @Bean
    public GroupedOpenApi billingEngineApi() {
        return GroupedOpenApi.builder()
                .group("05-billing-engine")
                .displayName("L5 — Billing Engine (Pricing, Invoice, Wallet, Reports)")
                .pathsToMatch("/api/v1/billing/**", "/api/v1/invoices/**",
                              "/api/v1/wallets/**", "/api/v1/pricing/**")
                .build();
    }

    // ── Bearer scheme description ─────────────────────────────────────────────

    /**
     * Builds the Swagger UI description for the Bearer JWT security scheme.
     * Documents all TiiBnTick business roles from {@code TntRole} enum and the
     * permission string format used by {@code @RequirePermission}.
     *
     * @return formatted description string
     */
    private String buildBearerSchemeDescription() {
        return """
                JWT issued by YowAuth0 (comops-auth-core). Obtain via POST /api/auth/login.

                **TiiBnTick Roles (TntRole enum — tnt-roles-core):**
                | Role Code | Scope | Description |
                |---|---|---|
                | `AGENCY_MANAGER` | AGENCY | Full agency management: staff, missions, billing |
                | `BRANCH_MANAGER` | AGENCY | Daily operations of an antenne (branch) |
                | `PERMANENT_DELIVERER` | AGENCY | Salaried deliverer attached to an agency |
                | `FREELANCER` | TENANT | Independent deliverer responding to announcements |
                | `RELAY_OPERATOR` | AGENCY | Hub/relay point operator |
                | `CLIENT` | TENANT | End client — announces, tracks, pays |
                | `SUPPORT_AGENT` | TENANT | Customer support — read-only + dispute resolution |
                | `ORG_ADMIN` | ORGANIZATION | Multi-agency organization administrator |
                | `TNT_ADMIN` | SYSTEM | Platform-wide super-admin (wildcard `*` permission) |

                **Permission Format (resource:action[#SCOPE]):**
                - `mission:create` — exact match
                - `mission:*` — resource wildcard (any action)
                - `*` — global wildcard (TNT_ADMIN only)
                - `delivery:confirm#AGENCY:<agencyId>` — narrowly scoped

                **JWT Claims:**
                - `roles` → `ROLE_*` authorities (e.g. `ROLE_AGENCY_MANAGER`)
                - `permissions` → raw permission strings (e.g. `mission:create#AGENCY:<id>`)
                - `tid` → tenant UUID
                - `actor` → actor UUID (linked to DelivererProfile / FreelancerProfile)
                - `aid` → agency UUID
                - `oid` → organization UUID
                """;
    }
}
