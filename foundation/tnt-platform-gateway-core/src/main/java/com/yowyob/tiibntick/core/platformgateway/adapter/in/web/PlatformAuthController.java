package com.yowyob.tiibntick.core.platformgateway.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.platformgateway.application.port.in.ProxyKernelAuthUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Single entry point for platform backends (Agency, Go, Link, Market, Point Relais, ...)
 * to authenticate and manage user sessions — talks to TiiBnTick Core only, never to the
 * Kernel directly (see root {@code CLAUDE.md} and {@code docs/kernel-api/}).
 *
 * <p>Every method here mirrors exactly one Kernel {@code auth-controller} endpoint
 * (path, request body, response payload) and delegates the actual business — captcha,
 * OTP, MFA, password reset, sign-up, login, context selection — to the Kernel via
 * {@link ProxyKernelAuthUseCase}. TiiBnTick Core performs NO cryptography, NO password
 * checking, and NO credential storage of its own: it only routes and re-wraps the
 * response in the Core's standard {@link ApiResponse} envelope.
 *
 * <p>Request/response bodies are forwarded as opaque JSON rather than re-declared as
 * ~25 one-off DTOs — see {@code KernelApiEnvelope}'s javadoc for why. Platform backends
 * can keep sending the exact same JSON shapes they already send to the Kernel today —
 * only the base URL changes.
 *
 * <p>Requires a valid platform {@code X-Client-Id}/{@code X-Api-Key} pair with the
 * {@code AUTH} scope (see {@code TntPlatformGatewaySecurityConfig}) — no TiiBnTick JWT
 * is required here, since these ARE the bootstrap endpoints platforms call before they
 * hold one. The caller's own {@code Authorization} header (if any — relevant for
 * {@code mfa/disable}, {@code users/{userId}/reset-password}) is forwarded to the Kernel
 * unchanged; the Kernel remains the sole authority on whether it's valid/sufficient.
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Platform Auth Gateway", description = "Kernel auth-controller proxy for platform backends (Agency, Go, ...)")
@SecurityRequirement(name = "ClientIdAuth")
@SecurityRequirement(name = "ApiKeyAuth")
public class PlatformAuthController {

    private final ProxyKernelAuthUseCase useCase;

    public PlatformAuthController(ProxyKernelAuthUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/captcha")
    @Operation(summary = "Issue a captcha challenge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> captcha(
            @RequestBody(required = false) JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/captcha", body, authorization);
    }

    @PostMapping("/captcha/verify")
    @Operation(summary = "Verify a captcha challenge response")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> captchaVerify(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/captcha/verify", body, authorization);
    }

    @PostMapping("/discover-contexts")
    @Operation(summary = "Discover the login contexts (tenants/organizations) available to a principal")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> discoverContexts(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/discover-contexts", body, authorization);
    }

    @PostMapping("/discover-sign-up-contexts")
    @Operation(summary = "Discover the sign-up contexts available for a new account")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> discoverSignUpContexts(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/discover-sign-up-contexts", body, authorization);
    }

    @PostMapping("/email-verification/confirm")
    @Operation(summary = "Confirm an email verification challenge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> confirmEmailVerification(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/email-verification/confirm", body, authorization);
    }

    @PostMapping("/email-verification/request")
    @Operation(summary = "Request an email verification challenge for the current user")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> requestEmailVerification(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/email-verification/request", null, authorization);
    }

    @PostMapping("/email-verification/resend")
    @Operation(summary = "Resend a previously issued email verification challenge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> resendEmailVerification(
            @RequestBody(required = false) JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/email-verification/resend", body, authorization);
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Start the forgot-password flow for a principal")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> forgotPassword(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/forgot-password", body, authorization);
    }

    @PostMapping("/identify")
    @Operation(summary = "Identify an account by principal (email/phone/username) before login")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> identify(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/identify", body, authorization);
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with principal + password")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> login(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/login", body, authorization);
    }

    @PostMapping("/login/mfa/confirm")
    @Operation(summary = "Confirm the MFA challenge issued during login")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> confirmLoginMfa(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/login/mfa/confirm", body, authorization);
    }

    @PostMapping("/mfa/confirm")
    @Operation(summary = "Confirm an MFA enrollment/verification challenge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> confirmMfa(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/mfa/confirm", body, authorization);
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "Disable MFA for the current user (requires the user's own Bearer token)")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> disableMfa(
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/mfa/disable", null, authorization);
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Enable MFA for the current user (requires the user's own Bearer token)")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> enableMfa(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/mfa/enable", body, authorization);
    }

    @PostMapping("/otp")
    @Operation(summary = "Issue an OTP challenge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> issueOtp(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/otp", body, authorization);
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify an OTP challenge response")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> verifyOtp(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/otp/verify", body, authorization);
    }

    @PostMapping("/password-reset/issue")
    @Operation(summary = "Issue a password-reset challenge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> issuePasswordReset(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/password-reset/issue", body, authorization);
    }

    @PostMapping("/phone-verification/confirm")
    @Operation(summary = "Confirm a phone verification challenge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> confirmPhoneVerification(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/phone-verification/confirm", body, authorization);
    }

    @PostMapping("/phone-verification/request")
    @Operation(summary = "Request a phone verification challenge")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> requestPhoneVerification(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/phone-verification/request", body, authorization);
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new user account")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> register(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/register", body, authorization);
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Complete a password reset using an issued challenge token")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> resetPassword(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/reset-password", body, authorization);
    }

    @PostMapping("/select-context")
    @Operation(summary = "Select a discovered login context and obtain an access token")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> selectContext(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/select-context", body, authorization);
    }

    @PostMapping("/sign-up")
    @Operation(summary = "Public self-service sign-up")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> signUp(
            @RequestBody JsonNode body,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/sign-up", body, authorization);
    }

    @PostMapping("/users/{userId}/reset-password")
    @Operation(summary = "Admin-initiated password reset for a given user (requires the caller's own Bearer token)")
    public Mono<ResponseEntity<ApiResponse<JsonNode>>> adminResetPassword(
            @PathVariable UUID userId,
            @Parameter(hidden = true) @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        return proxy("/api/auth/users/" + userId + "/reset-password", null, authorization);
    }

    // ── Shared proxy helper ─────────────────────────────────────────────────

    private Mono<ResponseEntity<ApiResponse<JsonNode>>> proxy(String kernelPath, JsonNode body, String authorization) {
        return useCase.callAuth(HttpMethod.POST, kernelPath, body, authorization)
                .map(result -> ResponseEntity.status(result.httpStatus()).body(result.envelope().toApiResponse()));
    }
}
