package com.yowyob.tiibntick.core.auth.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.auth.application.port.in.ProxyKernelAuthUseCase;
import com.yowyob.tiibntick.core.auth.application.port.in.ProxyKernelSsoUseCase;
import com.yowyob.tiibntick.core.auth.config.TntPlatformGatewayProperties;
import com.yowyob.tiibntick.core.auth.domain.exception.TntAuthException;
import com.yowyob.tiibntick.core.auth.domain.model.KernelRawResponse;
import com.yowyob.tiibntick.core.auth.domain.model.ResolveSsoContextRequest;
import com.yowyob.tiibntick.core.auth.domain.model.ResolveSsoContextResponse;
import com.yowyob.tiibntick.core.auth.domain.model.SsoLaunchRequest;
import com.yowyob.tiibntick.core.auth.domain.model.SsoLaunchResponse;
import com.yowyob.tiibntick.core.auth.domain.model.SsoTokenExchangeRequest;
import com.yowyob.tiibntick.core.auth.domain.model.SsoTokenExchangeResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Implements {@link ProxyKernelSsoUseCase} — orchestrates the Kernel's raw OAuth2/OIDC
 * endpoints ({@code GET /oauth2/userinfo}, {@code POST /oauth2/token}) via the same
 * {@link ProxyKernelAuthUseCase#callOidc} the raw {@code PlatformAuthOidcController} uses,
 * interpreting the JSON payloads into the stable, typed contracts platform backends
 * consume. Contains NO cryptography — the Kernel remains the sole token issuer/validator.
 *
 * <p><b>Known assumption:</b> the Kernel's {@code /oauth2/userinfo} response shape isn't
 * formally schema'd in its OpenAPI spec (declared as generic {@code object}) — this
 * service tries both {@code organizationId}/{@code id} and {@code contextId}/{@code id}
 * field-name variants when walking {@code contexts[].organizations[]}, matching
 * {@code CORE_KERNEL_GATEWAY_SPEC.md} §7.2's description of the existing
 * {@code KernelSsoClient.resolveContext()} logic. Adjust {@link #findMatchingContext} if
 * the live field names differ.
 *
 * @author MANFOUO Braun
 */
public class KernelSsoGatewayService implements ProxyKernelSsoUseCase {

    private final ProxyKernelAuthUseCase kernelAuthUseCase;
    private final ObjectMapper objectMapper;
    private final TntPlatformGatewayProperties properties;

    public KernelSsoGatewayService(ProxyKernelAuthUseCase kernelAuthUseCase, ObjectMapper objectMapper, TntPlatformGatewayProperties properties) {
        this.kernelAuthUseCase = kernelAuthUseCase;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Mono<ResolveSsoContextResponse> resolveContext(ResolveSsoContextRequest request, String bearerAuthorization) {
        String bearer = bearerAuthorization != null && !bearerAuthorization.isBlank()
                ? bearerAuthorization
                : "Bearer " + request.sharedSessionToken();
        return kernelAuthUseCase.callOidc(HttpMethod.GET, "/oauth2/userinfo", null, null, bearer)
                .flatMap(response -> parseUserInfo(response, request.kernelOrganizationId()));
    }

    @Override
    public Mono<SsoTokenExchangeResponse> exchangeToken(SsoTokenExchangeRequest request) {
        byte[] form = buildTokenExchangeForm(request).getBytes(StandardCharsets.UTF_8);
        return kernelAuthUseCase.callOidc(HttpMethod.POST, "/oauth2/token", MediaType.APPLICATION_FORM_URLENCODED, form, null)
                .flatMap(this::parseTokenResponse);
    }

    @Override
    public Mono<SsoLaunchResponse> launch(SsoLaunchRequest request) {
        String redirectBase = properties.getSsoAppRedirectUrls().get(request.app());
        if (redirectBase == null) {
            return Mono.error(TntAuthException.ssoAppNotConfigured(request.app()));
        }
        ResolveSsoContextRequest resolveRequest = new ResolveSsoContextRequest(request.sharedSessionToken(), request.kernelOrganizationId());
        return resolveContext(resolveRequest, "Bearer " + request.sharedSessionToken())
                .flatMap(resolved -> exchangeToken(new SsoTokenExchangeRequest(
                        request.sharedSessionToken(), resolved.contextId(), resolved.organizationId(),
                        request.app(), request.kernelAgencyId()))
                        .map(tokenResponse -> buildLaunchResponse(request, redirectBase, tokenResponse, resolved)));
    }

    // ── userinfo parsing ─────────────────────────────────────────────────────

    private Mono<ResolveSsoContextResponse> parseUserInfo(KernelRawResponse response, String kernelOrganizationId) {
        if (!isSuccess(response)) {
            return Mono.error(TntAuthException.ssoContextNotFound(kernelOrganizationId));
        }
        return findMatchingContext(response, kernelOrganizationId)
                .map(Mono::just)
                .orElseGet(() -> Mono.error(TntAuthException.ssoContextNotFound(kernelOrganizationId)));
    }

    private Optional<ResolveSsoContextResponse> findMatchingContext(KernelRawResponse response, String kernelOrganizationId) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            for (JsonNode context : root.path("contexts")) {
                for (JsonNode organization : context.path("organizations")) {
                    String orgId = firstNonBlank(textOrNull(organization, "organizationId"), textOrNull(organization, "id"));
                    if (kernelOrganizationId.equals(orgId)) {
                        String contextId = firstNonBlank(textOrNull(context, "contextId"), textOrNull(context, "id"));
                        return Optional.of(new ResolveSsoContextResponse(contextId, orgId));
                    }
                }
            }
        } catch (Exception ignored) {
            // malformed/unexpected userinfo shape — treated as "not found" by the caller
        }
        return Optional.empty();
    }

    // ── token exchange ───────────────────────────────────────────────────────

    private String buildTokenExchangeForm(SsoTokenExchangeRequest request) {
        StringBuilder form = new StringBuilder();
        appendFormField(form, "grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        appendFormField(form, "subject_token_type", "urn:ietf:params:oauth:token-type:jwt");
        appendFormField(form, "subject_token", request.sharedSessionToken());
        appendFormField(form, "context_id", request.contextId());
        appendFormField(form, "organization_id", request.organizationId());
        appendFormField(form, "service_code", request.serviceCode());
        if (request.agencyId() != null && !request.agencyId().isBlank()) {
            appendFormField(form, "agency_id", request.agencyId());
        }
        return form.toString();
    }

    private void appendFormField(StringBuilder form, String key, String value) {
        if (!form.isEmpty()) {
            form.append('&');
        }
        form.append(URLEncoder.encode(key, StandardCharsets.UTF_8))
                .append('=')
                .append(URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8));
    }

    private Mono<SsoTokenExchangeResponse> parseTokenResponse(KernelRawResponse response) {
        if (!isSuccess(response)) {
            return Mono.error(TntAuthException.ssoTokenExchangeFailed("Kernel returned HTTP " + response.status()));
        }
        try {
            JsonNode root = objectMapper.readTree(response.body());
            String token = firstNonBlank(textOrNull(root, "access_token"), textOrNull(root, "accessToken"));
            if (token == null) {
                return Mono.error(TntAuthException.ssoTokenExchangeFailed("no access_token in Kernel response"));
            }
            return Mono.just(new SsoTokenExchangeResponse(token));
        } catch (Exception e) {
            return Mono.error(TntAuthException.ssoTokenExchangeFailed(e.getMessage()));
        }
    }

    // ── launch composite ─────────────────────────────────────────────────────

    private SsoLaunchResponse buildLaunchResponse(SsoLaunchRequest request, String redirectBase,
            SsoTokenExchangeResponse tokenResponse, ResolveSsoContextResponse resolved) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(redirectBase)
                .queryParam("token", tokenResponse.accessToken())
                .queryParam("organizationId", resolved.organizationId());
        if (request.branchName() != null && !request.branchName().isBlank()) {
            builder.queryParam("branch", request.branchName());
        }
        return new SsoLaunchResponse(builder.build().toUriString(), request.app(), request.branchName() != null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static boolean isSuccess(KernelRawResponse response) {
        return response.status() >= 200 && response.status() < 300;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() ? value.asText(null) : null;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
