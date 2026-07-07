package com.yowyob.tiibntick.core.billing.dsl.infrastructure.adapter.in.web;

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
    @Operation(summary = "Create a new DSL tariff rule")
    public Mono<DslRuleResponse> create(@Valid @RequestBody DslRuleRequest request) {
        return dslRuleUseCase.createRule(toDomain(null, request))
                .map(DslRuleResponse::from);
    }

    @PutMapping("/rules/{ruleId}")
    @Operation(summary = "Update an existing DSL tariff rule")
    public Mono<DslRuleResponse> update(
            @PathVariable UUID ruleId,
            @Valid @RequestBody DslRuleRequest request) {
        return dslRuleUseCase.updateRule(toDomain(ruleId, request))
                .map(DslRuleResponse::from);
    }

    @GetMapping("/rules/{ruleId}")
    @Operation(summary = "Get a DSL rule by ID")
    public Mono<DslRuleResponse> getById(@PathVariable UUID ruleId) {
        return dslRuleUseCase.findById(ruleId)
                .map(DslRuleResponse::from);
    }

    @GetMapping("/policies/{policyId}/rules")
    @Operation(summary = "List all rules for a billing policy (ordered by priority)")
    public Flux<DslRuleResponse> listByPolicy(@PathVariable UUID policyId) {
        return dslRuleUseCase.findAllByPolicyId(policyId)
                .map(DslRuleResponse::from);
    }

    @GetMapping("/policies/{policyId}/rules/active")
    @Operation(summary = "List active rules for a billing policy (ordered by priority)")
    public Flux<DslRuleResponse> listActiveByPolicy(@PathVariable UUID policyId) {
        return dslRuleUseCase.findActiveByPolicyId(policyId)
                .map(DslRuleResponse::from);
    }

    @PatchMapping("/rules/{ruleId}/activate")
    @Operation(summary = "Activate a DSL rule")
    public Mono<DslRuleResponse> activate(@PathVariable UUID ruleId) {
        return dslRuleUseCase.activateRule(ruleId)
                .map(DslRuleResponse::from);
    }

    @PatchMapping("/rules/{ruleId}/deactivate")
    @Operation(summary = "Deactivate a DSL rule")
    public Mono<DslRuleResponse> deactivate(@PathVariable UUID ruleId) {
        return dslRuleUseCase.deactivateRule(ruleId)
                .map(DslRuleResponse::from);
    }

    @DeleteMapping("/rules/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a DSL rule")
    public Mono<Void> delete(@PathVariable UUID ruleId) {
        return dslRuleUseCase.deleteRule(ruleId);
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
    public Mono<EvaluationResultResponse> evaluate(@Valid @RequestBody EvaluateRequest request) {
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
}
