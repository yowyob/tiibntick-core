package com.yowyob.tiibntick.core.notify.adapter.in.web;

import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationPreferencesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationProvidersUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationRemindersUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationTemplatesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISearchNotificationsUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IViewKernelDeliveriesUseCase;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationDeliveryRecord;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationProviderConfig;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationReminder;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationTemplateConfig;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * REST controller for notification management.
 *
 * <p>Exposes endpoints for:
 * <ul>
 *   <li>Sending notifications across any supported channel (push, SMS, WhatsApp, email, in-app)</li>
 *   <li>Querying notification history for a recipient</li>
 *   <li>Managing per-user notification preferences and channel opt-in/opt-out</li>
 * </ul>
 *
 * <p>URL prefix: {@code /api/v1/notifications}
 *
 * <p>Endpoints that delegate to the Kernel notification engine (send,
 * preferences, providers, templates, reminders, kernel-deliveries) require
 * {@code X-Tenant-Id} — the Kernel's gateway rejects those calls without it
 * ({@code TENANT_CONTEXT_REQUIRED} / {@code ORGANIZATION_CONTEXT_REQUIRED}),
 * mirroring the {@code X-Tenant-Id}/{@code X-Organization-Id} convention
 * already used by other TiiBnTick controllers (e.g. {@code ProductController}).
 *
 * @author MANFOUO Braun
 */
@Tag(name = "Notification Management", description = "Send, query and configure notifications")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final ISendNotificationUseCase sendNotificationUseCase;
    private final ISearchNotificationsUseCase searchNotificationsUseCase;
    private final IManageNotificationPreferencesUseCase managePreferencesUseCase;
    private final IManageNotificationProvidersUseCase manageProvidersUseCase;
    private final IManageNotificationTemplatesUseCase manageTemplatesUseCase;
    private final IManageNotificationRemindersUseCase manageRemindersUseCase;
    private final IViewKernelDeliveriesUseCase viewKernelDeliveriesUseCase;

    // ─── Send ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Send a notification to a recipient via a specific channel",
            description = "Translates the i18n template and dispatches to the recipient's channel. "
                    + "Supported channels: PUSH_FCM, SMS_LOCAL, WHATSAPP, EMAIL, IN_APP_WEBSOCKET")
    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','SUPPORT_AGENT','TNT_ADMIN')")
    public Mono<Notification> send(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @Valid @RequestBody SendNotificationRequest request) {
        NotificationModel model = new NotificationModel(
                request.templateKey(),
                request.targetLanguage() != null ? request.targetLanguage() : SupportedLanguage.FR_CM.getTag(),
                request.parameters() != null ? request.parameters() : Map.of(),
                request.priority() != null
                        ? NotificationPriority.valueOf(request.priority())
                        : NotificationPriority.NORMAL);

        return sendNotificationUseCase.send(
                currentUser.tenantId().toString(),
                organizationId != null ? organizationId.toString() : null,
                request.recipientId(),
                request.targetDestination(),
                model,
                NotificationChannel.valueOf(request.channel()));
    }

    // ─── Query ─────────────────────────────────────────────────────────────────
    // Local search history — TiiBnTick's own send pipeline, not tenant-scoped
    // at the query level (recipientId/notificationId are already unique).

    @Operation(summary = "Get a single notification by ID")
    @GetMapping("/{notificationId}")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TNT_ADMIN')")
    public Mono<Notification> getById(@PathVariable String notificationId) {
        return searchNotificationsUseCase.findById(notificationId);
    }

    @Operation(summary = "List all notifications for a recipient")
    @GetMapping("/recipient/{recipientId}")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','SUPPORT_AGENT','TNT_ADMIN')")
    public Flux<Notification> listByRecipient(@PathVariable String recipientId) {
        return searchNotificationsUseCase.findByRecipientId(recipientId);
    }

    @Operation(summary = "List pending (unread) notifications for a recipient")
    @GetMapping("/recipient/{recipientId}/pending")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','SUPPORT_AGENT','TNT_ADMIN')")
    public Flux<Notification> listPending(@PathVariable String recipientId) {
        return searchNotificationsUseCase.findPendingByRecipient(recipientId);
    }

    @Operation(summary = "List notifications filtered by delivery status")
    @GetMapping("/by-status/{status}")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TNT_ADMIN')")
    public Flux<Notification> listByStatus(@PathVariable String status) {
        return searchNotificationsUseCase.findByStatus(DeliveryStatus.valueOf(status));
    }

    // ─── Preferences ───────────────────────────────────────────────────────────

    @Operation(summary = "Get notification preferences for a user")
    @GetMapping("/preferences/{userId}")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','SUPPORT_AGENT','TNT_ADMIN')")
    public Mono<NotificationPreference> getPreferences(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PathVariable String userId) {
        return managePreferencesUseCase.getPreferences(currentUser.tenantId().toString(), orgOrNull(organizationId), userId);
    }

    @Operation(summary = "Save or update notification preferences for a user")
    @PutMapping("/preferences/{userId}")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','TNT_ADMIN')")
    public Mono<NotificationPreference> savePreferences(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PathVariable String userId,
            @Valid @RequestBody UpdatePreferencesRequest body) {
        NotificationPreference pref = new NotificationPreference(
                userId,
                currentUser.tenantId().toString(),
                orgOrNull(organizationId),
                body.activeChannels() != null ? body.activeChannels() : java.util.Set.of(),
                body.preferredLanguage(),
                body.notificationsEnabled() != null ? body.notificationsEnabled() : true);
        return managePreferencesUseCase.savePreferences(pref);
    }

    public record UpdatePreferencesRequest(
            java.util.Set<NotificationChannel> activeChannels,
            String preferredLanguage,
            Boolean notificationsEnabled) {}

    @Operation(summary = "Disable a notification channel for a user")
    @PostMapping("/preferences/{userId}/disable/{channel}")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','TNT_ADMIN')")
    public Mono<NotificationPreference> disableChannel(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PathVariable String userId,
            @PathVariable String channel) {
        return managePreferencesUseCase.disableChannel(currentUser.tenantId().toString(), orgOrNull(organizationId), userId,
                NotificationChannel.valueOf(channel));
    }

    @Operation(summary = "Enable a notification channel for a user")
    @PostMapping("/preferences/{userId}/enable/{channel}")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','TNT_ADMIN')")
    public Mono<NotificationPreference> enableChannel(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PathVariable String userId,
            @PathVariable String channel) {
        return managePreferencesUseCase.enableChannel(currentUser.tenantId().toString(), orgOrNull(organizationId), userId,
                NotificationChannel.valueOf(channel));
    }

    @Operation(summary = "Change the preferred notification language for a user")
    @PatchMapping("/preferences/{userId}/language")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','TNT_ADMIN')")
    public Mono<NotificationPreference> changeLanguage(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PathVariable String userId,
            @RequestParam String localeTag) {
        return managePreferencesUseCase.changeLanguage(currentUser.tenantId().toString(), orgOrNull(organizationId), userId,
                localeTag);
    }

    // ─── Kernel notification engine administration ─────────────────────────────
    // Manages the Kernel's (RT-comops) own delivery infrastructure — physical
    // providers (SMTP/Twilio/Meta/Firebase), reusable templates, and scheduled
    // reminders — plus raw visibility into what the Kernel actually attempted.

    @Operation(summary = "List physical delivery providers configured on the Kernel notification engine")
    @GetMapping("/providers")
    @PreAuthorize("hasRole('TNT_ADMIN')")
    public Flux<NotificationProviderConfig> listProviders(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return manageProvidersUseCase.listProviders(currentUser.tenantId().toString(), orgOrNull(organizationId));
    }

    @Operation(summary = "Configure a physical delivery provider on the Kernel notification engine")
    @PostMapping("/providers")
    @PreAuthorize("hasRole('TNT_ADMIN')")
    public Mono<NotificationProviderConfig> saveProvider(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @Valid @RequestBody NotificationProviderConfig config) {
        return manageProvidersUseCase.saveProvider(currentUser.tenantId().toString(), orgOrNull(organizationId), config);
    }

    @Operation(summary = "List message templates registered on the Kernel notification engine")
    @GetMapping("/templates")
    @PreAuthorize("hasRole('TNT_ADMIN')")
    public Flux<NotificationTemplateConfig> listTemplates(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return manageTemplatesUseCase.listTemplates(currentUser.tenantId().toString(), orgOrNull(organizationId));
    }

    @Operation(summary = "Register or update a message template on the Kernel notification engine")
    @PostMapping("/templates")
    @PreAuthorize("hasRole('TNT_ADMIN')")
    public Mono<NotificationTemplateConfig> saveTemplate(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @Valid @RequestBody NotificationTemplateConfig config) {
        return manageTemplatesUseCase.saveTemplate(currentUser.tenantId().toString(), orgOrNull(organizationId), config);
    }

    @Operation(summary = "List reminders scheduled on the Kernel notification engine")
    @GetMapping("/reminders")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TNT_ADMIN')")
    public Flux<NotificationReminder> listReminders(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return manageRemindersUseCase.listReminders(currentUser.tenantId().toString(), orgOrNull(organizationId));
    }

    @Operation(summary = "Schedule a future notification on the Kernel notification engine")
    @PostMapping("/reminders")
    @PreAuthorize("hasAnyRole('SUPPORT_AGENT','TNT_ADMIN')")
    public Mono<NotificationReminder> scheduleReminder(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @Valid @RequestBody NotificationReminder reminder) {
        return manageRemindersUseCase.scheduleReminder(currentUser.tenantId().toString(), orgOrNull(organizationId), reminder);
    }

    @Operation(summary = "List raw delivery records tracked by the Kernel notification engine (troubleshooting)")
    @GetMapping("/kernel-deliveries")
    @PreAuthorize("hasRole('TNT_ADMIN')")
    public Flux<NotificationDeliveryRecord> listKernelDeliveries(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return viewKernelDeliveriesUseCase.listKernelDeliveries(currentUser.tenantId().toString(), orgOrNull(organizationId));
    }

    private static String orgOrNull(UUID organizationId) {
        return organizationId != null ? organizationId.toString() : null;
    }

    // ─── Request DTO ───────────────────────────────────────────────────────────

    /**
     * Request body for sending a notification.
     *
     * @param recipientId       Actor ID of the recipient
     * @param targetDestination Physical destination (phone number, FCM token, email)
     * @param channel           Channel name — one of: PUSH_FCM, SMS_LOCAL, WHATSAPP, EMAIL, IN_APP_WEBSOCKET
     * @param templateKey       i18n message template key (registered in yow-i18n-kernel)
     * @param targetLanguage    BCP 47 language tag (e.g. "fr", "en"). Defaults to "fr" when null.
     * @param parameters        Template variable substitutions
     * @param priority          NORMAL | HIGH | CRITICAL. Defaults to NORMAL when null.
     */
    public record SendNotificationRequest(
            @NotBlank String recipientId,
            @NotBlank String targetDestination,
            @NotBlank String channel,
            @NotBlank String templateKey,
            String targetLanguage,
            Map<String, Object> parameters,
            String priority
    ) {}
}
