package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.service.InAppNotificationService;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Notification;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * Lot C-36 — the caller's in-app notifications (sender, courier or relay owner alike). The recipient
 * is always the token's user: there is no recipient id in any path.
 */
@RestController
@RequestMapping("/api/v1/gofp/notifications")
@RequiredArgsConstructor
public class GofpNotificationController {

    private final InAppNotificationService inbox;

    public record NotificationView(UUID id, String type, String title, String message, String params,
                                   String linkKind, UUID linkId, Instant createdAt, Instant readAt) {}

    public record InboxView(List<NotificationView> items, long unread, int page, int size) {}

    public record ReadAllView(long updated) {}

    private static UUID callerId(TntSecurityContext ctx) {
        return ctx != null ? ctx.userId() : null;
    }

    @GetMapping("/mine")
    public Mono<InboxView> mine(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "30") int size,
                                @CurrentUser(required = false) TntSecurityContext ctx) {
        int bounded = Math.max(1, Math.min(InAppNotificationService.MAX_PAGE, size));
        return Mono.defer(() -> inbox.mine(callerId(ctx), page, bounded))
                .map(i -> new InboxView(i.items().stream().map(GofpNotificationController::view).toList(),
                        i.unread(), Math.max(0, page), bounded));
    }

    @PostMapping("/{id}/read")
    public Mono<NotificationView> read(@PathVariable UUID id, @CurrentUser(required = false) TntSecurityContext ctx) {
        return Mono.defer(() -> inbox.markRead(id, callerId(ctx))).map(GofpNotificationController::view);
    }

    @PostMapping("/read-all")
    public Mono<ReadAllView> readAll(@CurrentUser(required = false) TntSecurityContext ctx) {
        return Mono.defer(() -> inbox.markAllRead(callerId(ctx))).map(ReadAllView::new);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id, @CurrentUser(required = false) TntSecurityContext ctx) {
        return Mono.defer(() -> inbox.delete(id, callerId(ctx)));
    }

    static NotificationView view(Notification n) {
        return new NotificationView(n.getId(), n.getNotificationType() != null ? n.getNotificationType().name() : null,
                n.getTitle(), n.getMessage(), n.getParams(), n.getLinkKind(), n.getLinkId(), n.getCreatedAt(),
                n.getReadAt());
    }
}
