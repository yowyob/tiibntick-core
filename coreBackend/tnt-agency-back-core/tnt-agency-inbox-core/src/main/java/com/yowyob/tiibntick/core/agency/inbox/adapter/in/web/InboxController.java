package com.yowyob.tiibntick.core.agency.inbox.adapter.in.web;

import com.yowyob.tiibntick.common.api.ApiResponse;
import com.yowyob.tiibntick.core.agency.inbox.adapter.in.web.dto.NotificationResponse;
import com.yowyob.tiibntick.core.agency.inbox.application.service.InboxService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;

/** Port of tnt-agency {@code NotificationInboxController}. */
@Tag(name = "Agency ERP Inbox", description = "Persisted agency notification inbox")
@RestController
@RequiredArgsConstructor
public class InboxController {

    private final InboxService inboxService;

    @PostMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/notifications")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Persist an inbox notification (BFF broadcasts SSE / notify-core separately)")
    public Mono<ApiResponse<NotificationResponse>> create(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @Valid @RequestBody CreateNotificationRequest body) {
        return inboxService.create(new InboxService.CreateInput(
                tenantId, agencyId, body.eventType(), body.title(), body.body(), body.href()))
                .map(ApiResponse::success);
    }

    @GetMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/notifications")
    @Operation(summary = "List persisted notifications for an agency inbox")
    public Mono<ApiResponse<List<NotificationResponse>>> list(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId,
            @RequestParam(defaultValue = "50") int limit) {
        return inboxService.listByAgency(tenantId, agencyId, limit)
                .collectList()
                .map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/notifications/{notificationId}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark a notification as read")
    public Mono<ApiResponse<NotificationResponse>> markRead(
            @PathVariable UUID tenantId,
            @PathVariable UUID notificationId) {
        return inboxService.markRead(tenantId, notificationId).map(ApiResponse::success);
    }

    @PatchMapping("/api/v1/tenants/{tenantId}/agency-registry/agencies/{agencyId}/notifications/read-all")
    @PreAuthorize("isAuthenticated()")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark all agency notifications as read")
    public Mono<Void> markAllRead(
            @PathVariable UUID tenantId,
            @PathVariable UUID agencyId) {
        return inboxService.markAllRead(tenantId, agencyId);
    }

    record CreateNotificationRequest(
            @NotBlank String eventType,
            @NotBlank String title,
            @NotBlank String body,
            String href) {}
}
