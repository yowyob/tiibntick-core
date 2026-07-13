package com.yowyob.tiibntick.core.linkback.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.LinkNotificationResponse;
import com.yowyob.tiibntick.core.linkback.adapter.in.web.response.LinkNotificationResponseMapper;
import com.yowyob.tiibntick.core.linkback.application.port.in.QueryLinkNotificationsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * Generic Link business API for the current user's own notifications — the single
 * entry point the Link BFF calls instead of reaching tnt-notify-core directly.
 *
 * <p>The recipient is always resolved from the authenticated identity ({@code @CurrentUser}),
 * never accepted as a request parameter — a caller can only ever read their own notifications.
 *
 * @author Dilane PAFE
 */
@Tag(name = "Link Notifications", description = "Notification inbox for the current Link user")
@RestController
@RequestMapping("/api/v1/platform/link/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
public class LinkNotificationController {

    private final QueryLinkNotificationsUseCase queryLinkNotificationsUseCase;

    @Operation(summary = "List all notifications for the current user")
    @GetMapping("/me")
    public Flux<LinkNotificationResponse> myNotifications(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryLinkNotificationsUseCase.forRecipient(resolveRecipientId(currentUser))
                .map(LinkNotificationResponseMapper::toResponse);
    }

    @Operation(summary = "List pending (unread) notifications for the current user")
    @GetMapping("/me/pending")
    public Flux<LinkNotificationResponse> myPendingNotifications(
            @Parameter(hidden = true) @CurrentUser TntUserIdentity currentUser) {
        return queryLinkNotificationsUseCase.pendingForRecipient(resolveRecipientId(currentUser))
                .map(LinkNotificationResponseMapper::toResponse);
    }

    private String resolveRecipientId(TntUserIdentity currentUser) {
        return (currentUser.actorId() != null ? currentUser.actorId() : currentUser.userId()).toString();
    }
}
