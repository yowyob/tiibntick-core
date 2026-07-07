package com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web;

import com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.request.ApplyTemplateRequest;
import com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.request.PreviewPriceRequest;
import com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.request.SaveCustomTemplateRequest;
import com.yowyob.tiibntick.core.billing.templates.adapter.inbound.web.response.*;
import com.yowyob.tiibntick.core.billing.templates.application.command.*;
import com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyOwnerType;
import com.yowyob.tiibntick.core.billing.templates.domain.model.TemplateCategory;
import com.yowyob.tiibntick.core.billing.templates.port.inbound.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * WebFlux REST controller for the billing policy templates module.
 *
 * <p>Exposes the full catalog browsing, template application, price preview,
 * and custom template management APIs.
 *
 * <p><b>Base path:</b> {@code /api/v1/billing/templates}
 *
 * <p><b>Security:</b> All endpoints require an authenticated actor. Admin-only
 * endpoints require the {@code ROLE_ADMIN} authority (enforced via Spring Security
 * in {@code tnt-auth-core}).
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/v1/billing/templates", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Billing Policy Templates", description = "Pre-defined billing policy template catalog management")
public class PolicyTemplateController {

    private final IListTemplatesUseCase listTemplatesUseCase;
    private final IApplyTemplateUseCase applyTemplateUseCase;
    private final IPreviewPriceUseCase previewPriceUseCase;
    private final ISaveCustomTemplateUseCase saveCustomTemplateUseCase;
    private final ICreateAdminTemplateUseCase createAdminTemplateUseCase;
    private final ResponseMapper responseMapper;

    // ═══════════════════════════════════════════════════════════════════════
    // CATALOG BROWSING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lists all active billing policy templates applicable to the given actor type.
     * Filters by optional category.
     *
     * @param ownerType the actor type requesting the catalog
     * @param category  optional category filter
     * @return Flux of applicable templates
     */
    @GetMapping
    @Operation(summary = "List billing policy templates",
               description = "Returns all active templates applicable to the given actor type, "
                           + "optionally filtered by category")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Templates listed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid ownerType parameter")
    })
    public Flux<PolicyTemplateResponse> listTemplates(
            @RequestParam @Parameter(description = "Actor type filter", example = "FREELANCER_ORG")
            PolicyOwnerType ownerType,
            @RequestParam(required = false) @Parameter(description = "Optional category filter", example = "SPECIALTY")
            TemplateCategory category) {

        log.debug("GET /api/v1/billing/templates?ownerType={}&category={}", ownerType, category);

        Flux<com.yowyob.tiibntick.core.billing.templates.domain.model.PolicyTemplate> templates =
                category != null
                        ? listTemplatesUseCase.listByCategory(ownerType, category)
                        : listTemplatesUseCase.listForOwnerType(ownerType);

        return templates.map(responseMapper::toResponse);
    }

    /**
     * Returns a single template by its business code.
     *
     * @param templateCode the template business key
     * @return the matching template
     */
    @GetMapping("/{templateCode}")
    @Operation(summary = "Get template by code", description = "Returns a single template by its unique code")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Template found"),
        @ApiResponse(responseCode = "404", description = "Template not found")
    })
    public Mono<PolicyTemplateResponse> getByCode(
            @PathVariable @Parameter(description = "Template code", example = "TPL-FRAGILE")
            String templateCode) {

        log.debug("GET /api/v1/billing/templates/{}", templateCode);
        return listTemplatesUseCase.getByCode(templateCode).map(responseMapper::toResponse);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // APPLY TEMPLATE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Applies a template and creates a new BillingPolicy for the actor.
     *
     * @param request the apply template request
     * @return the ID of the created BillingPolicy
     */
    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Apply a template",
               description = "Applies a billing policy template and creates a BillingPolicy in DRAFT state. "
                           + "Optionally saves a CustomPolicyTemplate for reuse.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "BillingPolicy created successfully"),
        @ApiResponse(responseCode = "400", description = "Validation error or template parameter out of range"),
        @ApiResponse(responseCode = "403", description = "Template not applicable to this actor type"),
        @ApiResponse(responseCode = "404", description = "Template not found"),
        @ApiResponse(responseCode = "410", description = "Template is inactive")
    })
    public Mono<ApplyTemplateResponse> applyTemplate(@Valid @RequestBody ApplyTemplateRequest request) {
        log.info("POST /api/v1/billing/templates/apply templateCode={} actor={} ownerType={}",
                request.getTemplateCode(), request.getOwnerActorId(), request.getOwnerType());

        ApplyTemplateCommand command = ApplyTemplateCommand.builder()
                .templateCode(request.getTemplateCode())
                .ownerActorId(request.getOwnerActorId())
                .ownerType(request.getOwnerType())
                .tenantId(request.getTenantId())
                .policyName(request.getPolicyName())
                .customizedParameters(request.getCustomizedParameters() != null
                        ? request.getCustomizedParameters() : Map.of())
                .saveAsCustomTemplate(request.isSaveAsCustomTemplate())
                .customTemplateName(request.getCustomTemplateName())
                .fromCustomTemplateId(request.getFromCustomTemplateId())
                .build();

        return applyTemplateUseCase.apply(command)
                .map(policyId -> responseMapper.toApplyResponse(
                        policyId,
                        request.getTemplateCode(),
                        request.getPolicyName() != null ? request.getPolicyName() : "Generated Policy",
                        request.isSaveAsCustomTemplate()));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRICE PREVIEW
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Computes a price estimate without creating any policy.
     *
     * @param request the preview request with template code, parameters, and sample scenario
     * @return the price preview result with full breakdown
     */
    @PostMapping("/preview")
    @Operation(summary = "Preview price from template",
               description = "Computes a price estimate for a sample scenario using the given template. "
                           + "No BillingPolicy is created. Read-only operation.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Preview computed"),
        @ApiResponse(responseCode = "400", description = "Invalid parameters or template code"),
        @ApiResponse(responseCode = "404", description = "Template not found")
    })
    public Mono<PreviewPriceResponse> previewPrice(@Valid @RequestBody PreviewPriceRequest request) {
        log.debug("POST /api/v1/billing/templates/preview templateCode={} distanceKm={} weightKg={}",
                request.getTemplateCode(), request.getDistanceKm(), request.getWeightKg());

        PreviewPriceCommand command = PreviewPriceCommand.builder()
                .templateCode(request.getTemplateCode())
                .ownerType(request.getOwnerType())
                .customizedParameters(request.getCustomizedParameters() != null
                        ? request.getCustomizedParameters() : Map.of())
                .distanceKm(request.getDistanceKm())
                .weightKg(request.getWeightKg())
                .packageType(request.getPackageType())
                .priority(request.getPriority())
                .clientTransactionCount(request.getClientTransactionCount())
                .deliveryZoneType(request.getDeliveryZoneType())
                .zoneAccessDifficulty(request.getZoneAccessDifficulty())
                .weatherCondition(request.getWeatherCondition())
                .paymentMethod(request.getPaymentMethod())
                .requiresRefrigeration(request.isRequiresRefrigeration())
                .requiresAssembly(request.isRequiresAssembly())
                .requiresIDCheck(request.isRequiresIDCheck())
                .timeOfDay(request.getTimeOfDay())
                .dayOfWeek(request.getDayOfWeek())
                .isPublicHoliday(request.isPublicHoliday())
                .storageHours(request.getStorageHours())
                .networkHopCount(request.getNetworkHopCount())
                .declaredValueXaf(request.getDeclaredValueXaf())
                .deliveryAttemptNumber(request.getDeliveryAttemptNumber())
                .packageCount(request.getPackageCount())
                .build();

        return previewPriceUseCase.preview(command).map(responseMapper::toResponse);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CUSTOM TEMPLATES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lists all personal custom templates saved by the given actor.
     *
     * @param ownerActorId the actor UUID
     * @return Flux of the actor's custom templates
     */
    @GetMapping("/custom")
    @Operation(summary = "List custom templates", description = "Lists all personal templates saved by an actor")
    public Flux<CustomPolicyTemplateResponse> listCustomTemplates(
            @RequestParam @Parameter(description = "Actor UUID", example = "550e8400-e29b-41d4-a716-446655440000")
            String ownerActorId) {

        log.debug("GET /api/v1/billing/templates/custom?ownerActorId={}", ownerActorId);
        return saveCustomTemplateUseCase.listByOwner(ownerActorId).map(responseMapper::toResponse);
    }

    /**
     * Saves a customized template configuration for future reuse.
     *
     * @param request the save request
     * @return the saved custom template
     */
    @PostMapping("/custom")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Save a custom template",
               description = "Saves a personalized template configuration for the actor to reuse later")
    public Mono<CustomPolicyTemplateResponse> saveCustomTemplate(
            @Valid @RequestBody SaveCustomTemplateRequest request) {

        log.info("POST /api/v1/billing/templates/custom name={} actor={}",
                request.getName(), request.getOwnerActorId());

        SaveCustomTemplateCommand command = SaveCustomTemplateCommand.builder()
                .ownerActorId(request.getOwnerActorId())
                .ownerType(request.getOwnerType())
                .tenantId(request.getTenantId())
                .name(request.getName())
                .sourceTemplateCode(request.getSourceTemplateCode())
                .customizedParameters(request.getCustomizedParameters())
                .build();

        return saveCustomTemplateUseCase.save(command).map(responseMapper::toResponse);
    }

    /**
     * Renames a custom template.
     *
     * @param customTemplateId the UUID of the custom template
     * @param ownerActorId     the requesting actor (must be the owner)
     * @param newName          the new display name
     * @return the updated custom template
     */
    @PatchMapping("/custom/{customTemplateId}/rename")
    @Operation(summary = "Rename a custom template")
    public Mono<CustomPolicyTemplateResponse> renameCustomTemplate(
            @PathVariable UUID customTemplateId,
            @RequestParam String ownerActorId,
            @RequestParam String newName) {

        return saveCustomTemplateUseCase.rename(customTemplateId, ownerActorId, newName)
                .map(responseMapper::toResponse);
    }

    /**
     * Deletes a custom template. Only the owner can delete their template.
     *
     * @param customTemplateId the UUID of the custom template
     * @param ownerActorId     the requesting actor (must be the owner)
     * @return 204 No Content on success
     */
    @DeleteMapping("/custom/{customTemplateId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a custom template")
    public Mono<Void> deleteCustomTemplate(
            @PathVariable UUID customTemplateId,
            @RequestParam String ownerActorId) {

        log.info("DELETE /api/v1/billing/templates/custom/{} actor={}", customTemplateId, ownerActorId);
        return saveCustomTemplateUseCase.delete(customTemplateId, ownerActorId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // ADMIN — Template Catalog Management
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * [Admin only] Lists all templates (including inactive).
     *
     * @return Flux of all templates
     */
    @GetMapping("/admin/all")
    @Operation(summary = "[Admin] List all templates",
               description = "Returns all templates including inactive ones. Requires ADMIN role.")
    public Flux<PolicyTemplateResponse> listAllTemplates() {
        log.debug("GET /api/v1/billing/templates/admin/all");
        return listTemplatesUseCase.listAll().map(responseMapper::toResponse);
    }

    /**
     * [Admin only] Activates a template.
     *
     * @param templateCode the template code to activate
     * @return the updated template
     */
    @PostMapping("/admin/{templateCode}/activate")
    @Operation(summary = "[Admin] Activate a template")
    public Mono<PolicyTemplateResponse> activateTemplate(@PathVariable String templateCode) {
        log.info("POST /api/v1/billing/templates/admin/{}/activate", templateCode);
        return createAdminTemplateUseCase.activate(templateCode).map(responseMapper::toResponse);
    }

    /**
     * [Admin only] Deactivates a template.
     *
     * @param templateCode the template code to deactivate
     * @return the updated template
     */
    @PostMapping("/admin/{templateCode}/deactivate")
    @Operation(summary = "[Admin] Deactivate a template")
    public Mono<PolicyTemplateResponse> deactivateTemplate(@PathVariable String templateCode) {
        log.info("POST /api/v1/billing/templates/admin/{}/deactivate", templateCode);
        return createAdminTemplateUseCase.deactivate(templateCode).map(responseMapper::toResponse);
    }

    /**
     * [Admin only] Updates the default parameter values of a template.
     *
     * @param templateCode     the template code to update
     * @param newDefaultValues map of parameterKey → newDefaultValue
     * @return the updated template
     */
    @PatchMapping("/admin/{templateCode}/defaults")
    @Operation(summary = "[Admin] Update template default values",
               description = "Updates the default parameter values. Does not affect existing BillingPolicies.")
    public Mono<PolicyTemplateResponse> updateDefaults(
            @PathVariable String templateCode,
            @RequestBody Map<String, String> newDefaultValues) {

        log.info("PATCH /api/v1/billing/templates/admin/{}/defaults", templateCode);
        return createAdminTemplateUseCase.updateDefaultValues(templateCode, newDefaultValues)
                .map(responseMapper::toResponse);
    }
}
