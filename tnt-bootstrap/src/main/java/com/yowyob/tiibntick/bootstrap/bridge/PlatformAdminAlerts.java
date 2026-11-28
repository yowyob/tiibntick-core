package com.yowyob.tiibntick.bootstrap.bridge;

import com.yowyob.tiibntick.common.tenant.SystemTenant;
import com.yowyob.tiibntick.core.notify.application.port.in.IInAppInboxUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import com.yowyob.tiibntick.core.roles.application.port.out.IPlatformAdminAlertPort;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Alerts on every TNT_ADMIN grant or revocation, so that a suspicious grant is seen at once. Bridges tnt-roles-core
 * (L1) and tnt-notify-core (L3) here, in the assembly module, since neither may depend on the other for this.
 *
 * <ul>
 *   <li>Decision D-172: an <b>e-mail</b> to the beneficiary and to the system-tenant owner.</li>
 *   <li>Maintainer directive of 2026-11-27 (decision log, section 1): the owner also gets an automatic <b>in-app message</b> (Kernel
 *       inbox, D-157), and both are written in the owner's <b>own language</b>.</li>
 * </ul>
 *
 * <p>Language: never chosen here. Both channels resolve each recipient's per-account preference
 * ({@code i18n.user_language_preference}, the same rule as every notification); the platform default applies only
 * when the account has none. The beneficiary and the owner may therefore read the same event in two languages.
 *
 * <p>Addressed by Kernel user id only: the Core holds no e-mail address. That the Kernel resolves the address of a
 * {@code recipientUserId} on the EMAIL channel is contract check K20 (not proven live). Best-effort: a failure is
 * logged and never undoes the grant.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class PlatformAdminAlerts implements IPlatformAdminAlertPort {

    static final String GRANTED = "notify.roles.platform_admin.granted";
    static final String REVOKED = "notify.roles.platform_admin.revoked";
    /** Inbox tag of the platform's own messages (not a product's). */
    static final String INBOX_APPLICATION = "tiibntick-admin";

    private final ObjectProvider<ISendNotificationUseCase> notifications;
    private final ObjectProvider<IInAppInboxUseCase> inbox;

    public PlatformAdminAlerts(ObjectProvider<ISendNotificationUseCase> notifications,
                               ObjectProvider<IInAppInboxUseCase> inbox) {
        this.notifications = notifications;
        this.inbox = inbox;
    }

    @Override
    public Mono<Void> granted(PlatformRoleGrant grant, UUID systemOwnerUserId) {
        return alert(GRANTED, grant, systemOwnerUserId);
    }

    @Override
    public Mono<Void> revoked(PlatformRoleGrant grant, UUID systemOwnerUserId) {
        return alert(REVOKED, grant, systemOwnerUserId);
    }

    private Mono<Void> alert(String templateKey, PlatformRoleGrant g, UUID owner) {
        return emails(templateKey, g, owner).then(ownerInbox(templateKey, g, owner));
    }

    private Mono<Void> emails(String templateKey, PlatformRoleGrant g, UUID owner) {
        ISendNotificationUseCase sender = notifications.getIfAvailable();
        if (sender == null) {
            return Mono.empty();
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(g.userId());
        if (owner != null) {
            recipients.add(owner);
        }
        // no language hint: notify resolves each recipient's account language (default only when none is stored)
        NotificationModel model = NotificationModel.of(templateKey, null, params(g));
        String tenant = SystemTenant.id().toString();
        return Flux.fromIterable(recipients)
                .concatMap(r -> sender.send(tenant, null, r.toString(), null, model, NotificationChannel.EMAIL)
                        .doOnError(e -> log.warn("TNT_ADMIN e-mail {} to {} not sent: {}", templateKey, r, e.getMessage()))
                        .onErrorResume(e -> Mono.empty()))
                .then();
    }

    private Mono<Void> ownerInbox(String templateKey, PlatformRoleGrant g, UUID owner) {
        IInAppInboxUseCase messages = inbox.getIfAvailable();
        if (messages == null || owner == null) {
            if (owner == null) {
                log.warn("TNT_ADMIN alert {} for {}: no protected system owner recorded yet, no in-app message",
                        templateKey, g.userId());
            }
            return Mono.empty();
        }
        Map<String, String> metadata = Map.of("grantId", g.id().toString(), "event", templateKey);
        return messages.sendLocalized(owner, INBOX_APPLICATION, templateKey, params(g), null, metadata)
                .doOnError(e -> log.warn("TNT_ADMIN in-app message {} to owner {} not sent: {}",
                        templateKey, owner, e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .then();
    }

    static Map<String, Object> params(PlatformRoleGrant g) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", g.userId().toString());
        p.put("grantKind", g.kind().name());
        p.put("grantedBy", g.grantedBy().toString());
        p.put("sourceTenantId", g.sourceTenantId().toString());
        p.put("revokedBy", g.revokedBy() == null ? "-" : g.revokedBy().toString());
        p.put("reason", g.revokeReason() == null ? "-" : g.revokeReason());
        return p;
    }
}
