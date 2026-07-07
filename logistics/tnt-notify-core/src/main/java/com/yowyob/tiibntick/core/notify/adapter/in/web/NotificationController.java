package com.yowyob.tiibntick.core.notify.adapter.in.web;

import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationPreferencesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISearchNotificationsUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import io.swagger.v3.oas.annotations.Operation;
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

    // ─── Send ──────────────────────────────────────────────────────────────────

    @Operation(
            summary = "Send a notification to a recipient via a specific channel",
            description = "Translates the i18n template and dispatches to the recipient's channel. "
                    + "Supported channels: PUSH_FCM, SMS_LOCAL, WHATSAPP, EMAIL, IN_APP_WEBSOCKET")
    @PostMapping("/send")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('AGENCY_MANAGER','BRANCH_MANAGER','SUPPORT_AGENT','TNT_ADMIN')")
    public Mono<Notification> send(@Valid @RequestBody SendNotificationRequest request) {
        NotificationModel model = new NotificationModel(
                request.templateKey(),
                request.targetLanguage() != null ? request.targetLanguage() : "fr",
                request.parameters() != null ? request.parameters() : Map.of(),
                request.priority() != null
                        ? NotificationPriority.valueOf(request.priority())
                        : NotificationPriority.NORMAL);

        return sendNotificationUseCase.send(
                request.recipientId(),
                request.targetDestination(),
                model,
                NotificationChannel.valueOf(request.channel()));
    }

    // ─── Query ─────────────────────────────────────────────────────────────────

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
    public Mono<NotificationPreference> getPreferences(@PathVariable String userId) {
        return managePreferencesUseCase.getPreferences(userId);
    }

    @Operation(summary = "Save or update notification preferences for a user")
    @PutMapping("/preferences/{userId}")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','TNT_ADMIN')")
    public Mono<NotificationPreference> savePreferences(
            @PathVariable String userId,
            @Valid @RequestBody UpdatePreferencesRequest body) {
        NotificationPreference pref = new NotificationPreference(
                userId,
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
            @PathVariable String userId,
            @PathVariable String channel) {
        return managePreferencesUseCase.disableChannel(userId, NotificationChannel.valueOf(channel));
    }

    @Operation(summary = "Enable a notification channel for a user")
    @PostMapping("/preferences/{userId}/enable/{channel}")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','TNT_ADMIN')")
    public Mono<NotificationPreference> enableChannel(
            @PathVariable String userId,
            @PathVariable String channel) {
        return managePreferencesUseCase.enableChannel(userId, NotificationChannel.valueOf(channel));
    }

    @Operation(summary = "Change the preferred notification language for a user")
    @PatchMapping("/preferences/{userId}/language")
    @PreAuthorize("hasAnyRole('CLIENT','PERMANENT_DELIVERER','FREELANCER','TNT_ADMIN')")
    public Mono<NotificationPreference> changeLanguage(
            @PathVariable String userId,
            @RequestParam String localeTag) {
        return managePreferencesUseCase.changeLanguage(userId, localeTag);
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
