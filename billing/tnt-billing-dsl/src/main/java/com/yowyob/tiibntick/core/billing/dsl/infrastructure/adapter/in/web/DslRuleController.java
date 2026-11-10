package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslAccessLevel;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.DslRule;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.PricingContext;
import com.yowyob.tiibntick.core.billing.dsl.domain.model.ValidationError;
import com.yowyob.tiibntick.core.billing.dsl.domain.port.in.IDslRuleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inbound REST adapter (WebFlux) exposing the DSL billing rule API.
 *
 * <p>Base path: {@code /api/v1/billing/dsl}</p>
 *
 * <h3> additions</h3>
 * <ul>
 *   <li>{@code POST /validate-with-level} — validates a DSL expression against a
 *       specific {@link DslAccessLevel}.</li>
 *   <li>{@code POST /evaluate} — evaluate request DTO now includes all  context
 *       fields (FreelancerOrg, enriched parcel, geographic, client, temporal, Hub, Link).</li>
 * </ul>
 *
 * <h3>Security (Audit n°7 · #5 / #6)</h3>
 * <p>Single-resource endpoints ({@code getById}, {@code update}, {@code listByPolicy},
 * {@code listActiveByPolicy}, {@code activate}, {@code deactivate}, {@code delete}) resolve
 * the tenant from the JWT security context via {@code @CurrentUser TntUserIdentity} and pass
 * it down to {@link IDslRuleUseCase}, which scopes the repository lookup with
 * {@code findByIdAndTenantId} / {@code findByPolicyIdAndTenantId...} — closing an IDOR that
 * previously let any caller read or mutate another tenant's DSL rule by ID (or list another
 * tenant's rules given nothing but a policyId). Mutation endpoints are additionally gated
 * with {@code @PreAuthorize("isAuthenticated()")} as a minimum baseline: no dedicated
 * {@code dsl-rule:*} permission exists yet in the {@code tnt-roles-core} role catalog, so
 * fine-grained {@code @RequirePermission} enforcement is deferred until that catalog is
 * extended.</p>
 *
 * @author MANFOUO Braun
 */
@RestController
@RequestMapping("/api/v1/billing/dsl")
@RequiredArgsConstructor
@Tag(name = "Billing DSL", description = "Tariff rule DSL management and evaluation API")
public class DslRuleController {

    private final IDslRuleUseCase dslRuleUseCase;

    // ─────────────────── CRUD ────────────────────────────────────────────────

    @PostMapping("/rules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new DSL tariff rule")
    public Mono<DslRuleResponse> create(@CurrentUser TntUserIdentity currentUser,
                                        @Valid @RequestBody DslRuleRequest request) {
        assertStaff(currentUser);
        assertSameTenant(currentUser, request.tenantId());
        return dslRuleUseCase.createRule(toDomain(null, request))
                .map(DslRuleResponse::from);
    }

    @PutMapping("/rules/{ruleId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update an existing DSL tariff rule")
    public Mono<DslRuleResponse> update(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID ruleId,
            @Valid @RequestBody DslRuleRequest request) {
        assertStaff(currentUser);
        assertSameTenant(currentUser, request.tenantId());
        return dslRuleUseCase.updateRule(toDomain(ruleId, request), currentUser.tenantId())
                .map(DslRuleResponse::from);
    }

    @GetMapping("/rules/{ruleId}")
    @Operation(summary = "Get a DSL rule by ID")
    public Mono<DslRuleResponse> getById(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID ruleId) {
        assertStaff(currentUser);
        return dslRuleUseCase.findById(ruleId, currentUser.tenantId())
                .map(DslRuleResponse::from);
    }

    @GetMapping("/policies/{policyId}/rules")
    @Operation(summary = "List all rules for a billing policy (ordered by priority)")
    public Flux<DslRuleResponse> listByPolicy(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID policyId) {
        assertStaff(currentUser);
        return dslRuleUseCase.findAllByPolicyId(policyId, currentUser.tenantId())
                .map(DslRuleResponse::from);
    }

    @GetMapping("/policies/{policyId}/rules/active")
    @Operation(summary = "List active rules for a billing policy (ordered by priority)")
    public Flux<DslRuleResponse> listActiveByPolicy(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID policyId) {
        assertStaff(currentUser);
        return dslRuleUseCase.findActiveByPolicyId(policyId, currentUser.tenantId())
                .map(DslRuleResponse::from);
    }

    @PatchMapping("/rules/{ruleId}/activate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Activate a DSL rule")
    public Mono<DslRuleResponse> activate(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID ruleId) {
        assertStaff(currentUser);
        return dslRuleUseCase.activateRule(ruleId, currentUser.tenantId())
                .map(DslRuleResponse::from);
    }

    @PatchMapping("/rules/{ruleId}/deactivate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Deactivate a DSL rule")
    public Mono<DslRuleResponse> deactivate(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID ruleId) {
        assertStaff(currentUser);
        return dslRuleUseCase.deactivateRule(ruleId, currentUser.tenantId())
                .map(DslRuleResponse::from);
    }

    @DeleteMapping("/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a DSL rule")
    public Mono<Void> delete(
            @CurrentUser TntUserIdentity currentUser,
            @PathVariable UUID ruleId) {
        assertStaff(currentUser);
        return dslRuleUseCase.deleteRule(ruleId, currentUser.tenantId());
    }

    // ─────────────────── VALIDATION ──────────────────────────────────────────

    @PostMapping("/validate")
    @Operation(summary = "Validate a DSL condition expression without persisting")
    public Mono<ValidationResponse> validate(@RequestBody ValidateExpressionRequest request) {
        return dslRuleUseCase.validateExpression(request.expression())
                .map(errors -> new ValidationResponse(errors.isEmpty(), errors));
    }

    /**
     *  — Validates a DSL expression against a specific access level.
     * Returns errors if the expression uses variables or operators restricted
     * to a higher access level.
     */
    @PostMapping("/validate-with-level")
    @Operation(
            summary = "Validate a DSL condition expression against a specific access level",
            description = "Returns errors if the expression uses variables or operators "
                    + "restricted to FULL access when the caller has SIMPLIFIED access.")
    public Mono<ValidationResponse> validateWithLevel(
            @RequestBody ValidateWithLevelRequest request) {
        return dslRuleUseCase.validateExpressionWithAccessLevel(
                        request.expression(), request.accessLevel())
                .map(errors -> new ValidationResponse(errors.isEmpty(), errors));
    }

    // ─────────────────── EVALUATION ──────────────────────────────────────────

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate all active rules of a policy against a pricing context")
    public Mono<EvaluationResultResponse> evaluate(@CurrentUser TntUserIdentity currentUser,
                                                   @Valid @RequestBody EvaluateRequest request) {
        assertStaff(currentUser);
        assertSameTenant(currentUser, request.tenantId());
        PricingContext ctx = PricingContext.builder()
                // v1.0 — original fields
                .weightKg(request.weightKg())
                .distanceKm(request.distanceKm())
                .packageTypes(request.packageTypes())
                .priority(request.priority())
                .clientTxCount(request.clientTxCount() != null ? request.clientTxCount() : 0)
                .timeOfDay(request.timeOfDay())
                .weatherCondition(request.weatherCondition())
                .roadType(request.roadType())
                .tenantId(request.tenantId())
                .agencyId(request.agencyId())
                .missionId(request.missionId())
                //  — FreelancerOrg context
                .selectedVehicleType(request.selectedVehicleType())
                .activeEquipmentTypeCodes(request.activeEquipmentTypeCodes())
                .activatedSpecialization(request.activatedSpecialization())
                .isSubDelivererAssigned(request.isSubDelivererAssigned())
                //  — Enriched parcel context
                .packageCount(request.packageCount())
                .declaredValue(request.declaredValue())
                .requiresRefrigeration(request.requiresRefrigeration())
                .requiresAssembly(request.requiresAssembly())
                .requiresIDCheck(request.requiresIDCheck())
                .deliveryAttemptNumber(request.deliveryAttemptNumber())
                //  — Geographic context
                .deliveryZoneType(request.deliveryZoneType())
                .zoneAccessDifficulty(request.zoneAccessDifficulty())
                //  — Client context
                .paymentMethod(request.paymentMethod())
                .clientSegment(request.clientSegment())
                .isRecurringClient(request.isRecurringClient())
                //  — Extended temporal context
                .dayOfWeek(request.dayOfWeek())
                .isPublicHoliday(request.isPublicHoliday())
                //  — Policy owner context
                .policyOwnerType(request.policyOwnerType())
                .ownerActorId(request.ownerActorId())
                //  — Hub Point context
                .storageHours(request.storageHours())
                //  — Link Network context
                .networkHopCount(request.networkHopCount())
                .build();
        return dslRuleUseCase.evaluate(request.policyId(), ctx)
                .map(EvaluationResultResponse::from);
    }

    // ─────────────────── HELPERS ─────────────────────────────────────────────

    private DslRule toDomain(UUID id, DslRuleRequest request) {
        return DslRule.builder()
                .id(id)
                .name(request.name())
                .description(request.description())
                .conditionExpression(request.conditionExpression())
                .actionExpression(request.actionExpression())
                .priority(request.priority() != null ? request.priority() : 0)
                .active(request.active() != null ? request.active() : true)
                .tenantId(request.tenantId())
                .policyId(request.policyId())
                .createdAt(id == null ? Instant.now() : null)
                .updatedAt(Instant.now())
                .build();
    }

    // ─────────────────── INNER DTOs ──────────────────────────────────────────

    public record ValidateExpressionRequest(String expression) {}

    /**  — Validates expression with explicit access level. */
    public record ValidateWithLevelRequest(
            String expression,
            DslAccessLevel accessLevel) {}

    public record ValidationResponse(
            boolean valid,
            List<ValidationError> errors) {}

    /** A tenant id in the body must be the caller's own: it is not a way to reach another tenant's rules. */
    private static void assertSameTenant(TntUserIdentity user, UUID tenantId) {
        if (user == null || user.tenantId() == null || !user.tenantId().equals(tenantId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "tenantId must be the caller's own tenant");
        }
    }

    /**
     * DSL rules hang off a billing policy whose owner this module does not know, so managing or reading them is
     * reserved to billing management ({@code billing:write}) and platform admins. Drivers shape their tariff
     * through templates and their price grid, not raw rules. Pure syntax validation stays open to every caller.
     */
    private static void assertStaff(TntUserIdentity user) {
        boolean staff = user != null && (user.hasPermission("billing", "write")
                || user.hasPermission("tnt", "platform:admin")
                || user.permissions().contains("ROLE_TNT_ADMIN"));
        if (!staff) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "DSL rules can only be managed by billing management (billing:write)");
        }
    }
}
