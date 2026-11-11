package com.yowyob.tiibntick.core.notify.infrastructure.adapter.incident;

import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.tiibntick.core.incident.port.outbound.INotificationPort;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Adapter implementing {@link INotificationPort} (port defined in
 * tnt-incident-core).
 *
 * <p>
 * Bridges the incident module's notification requirements to the existing
 * {@link ISendNotificationUseCase} infrastructure. Incident notifications
 * are dispatched in-app (WebSocket) and additionally via Push FCM for
 * high-severity events.
 * </p>
 *
 * <p>
 * Channel routing strategy:
 * </p>
 * <ul>
 * <li>All incident notifications → {@code IN_APP_WEBSOCKET}</li>
 * <li>High-severity types → additionally {@code PUSH_FCM}</li>
 * </ul>
 *
 * <p>
 * Hexagonal position: secondary adapter in tnt-notify-core implementing
 * {@code INotificationPort} from tnt-incident-core. Assembled in tnt-bootstrap.
 * </p>
 *
 * <p>
 * {@code INotificationPort} (owned by tnt-incident-core) does not carry
 * tenant/organization context, so this adapter falls back to
 * {@code tnt.notify.kernel.default-tenant-id} / {@code default-organization-id}
 * (see {@link NotifyProperties.Kernel}) for the Push FCM path, which the
 * Kernel notification engine requires. Threading real per-incident tenant
 * context through would require extending {@code INotificationPort} itself —
 * out of scope here.
 * </p>
 *
 * @author MANFOUO Braun
 */
@Component
public class IncidentNotificationPortAdapter implements INotificationPort {

    private static final Logger log = LoggerFactory.getLogger(IncidentNotificationPortAdapter.class);

    /**
     * Incident notification types that also require Push FCM delivery
     * (high urgency — operator must be alerted even when app is backgrounded).
     */
    private static final List<String> HIGH_PRIORITY_TYPES = List.of(
            "INCIDENT_ESCALATED",
            "INCIDENT_AUTO_FAILED",
            "HANDOVER_CONFIRMED",
            "INTERAGENCY_COOP_REQUESTED",
            "INCIDENT_CREATED");

    private final ISendNotificationUseCase notificationUseCase;
    private final NotifyProperties properties;

    public IncidentNotificationPortAdapter(ISendNotificationUseCase notificationUseCase,
            NotifyProperties properties) {
        this.notificationUseCase = notificationUseCase;
        this.properties = properties;
    }

    private String defaultTenantId() {
        return properties.getKernel().getDefaultTenantId();
    }

    private String defaultOrganizationId() {
        return properties.getKernel().getDefaultOrganizationId();
    }

    /**
     * Sends an incident-related notification to a single actor.
     *
     * <p>
     * For high-priority incident types, a duplicate Push FCM notification is sent
     * in parallel with the in-app notification to ensure visibility when the app
     * is not in the foreground.
     * </p>
     *
     * @param actorId    UUID of the target actor (deliverer, manager, operator)
     * @param title      notification title
     * @param body       notification body text
     * @param type       notification type string (e.g. "INCIDENT_CREATED")
     * @param incidentId UUID of the related incident
     * @return Mono completing when all dispatches are done (errors suppressed
     *         individually)
     */
    @Override
    public Mono<Void> notifyActor(UUID actorId, String title, String body,
            String type, UUID incidentId) {
        return notifyActor(actorId, title, body, type, incidentId, Map.of());
    }

    @Override
    public Mono<Void> notifyActor(UUID actorId, String title, String body,
            String type, UUID incidentId, Map<String, Object> facts) {
        log.debug("Notifying actor={} incidentId={} type={}", actorId, incidentId, type);

        Map<String, Object> params = buildParams(title, body, incidentId, type, facts);
        NotificationModel model = buildModel(type, params, NotificationPriority.NORMAL);
        NotificationModel highModele = buildModel(type, params, NotificationPriority.HIGH);

        Mono<Void> inApp = notificationUseCase
                .send(defaultTenantId(), defaultOrganizationId(), actorId.toString(), actorId.toString(), model,
                        NotificationChannel.IN_APP_WEBSOCKET)
                .onErrorResume(ex -> {
                    log.warn("In-app notification failed for actor={}: {}", actorId, ex.getMessage());
                    return Mono.empty();
                })
                .then();

        if (HIGH_PRIORITY_TYPES.contains(type)) {
            Mono<Void> push = notificationUseCase
                    .send(defaultTenantId(), defaultOrganizationId(), actorId.toString(), actorId.toString(),
                            highModele, NotificationChannel.PUSH_FCM)
                    .onErrorResume(ex -> {
                        log.warn("Push notification failed for actor={}: {}", actorId, ex.getMessage());
                        return Mono.empty();
                    })
                    .then();
            return Mono.when(inApp, push);
        }

        return inApp;
    }

    /**
     * Sends an incident notification to a list of actors in parallel.
     *
     * @param actorIds   list of target actor UUIDs
     * @param title      notification title
     * @param body       notification body
     * @param type       notification type string
     * @param incidentId UUID of the related incident
     * @return Mono completing when all actor notifications are dispatched
     */
    @Override
    public Mono<Void> notifyActors(List<UUID> actorIds, String title, String body,
            String type, UUID incidentId) {
        return notifyActors(actorIds, title, body, type, incidentId, Map.of());
    }

    @Override
    public Mono<Void> notifyActors(List<UUID> actorIds, String title, String body,
            String type, UUID incidentId, Map<String, Object> facts) {
        if (actorIds == null || actorIds.isEmpty()) {
            return Mono.empty();
        }
        log.debug("Notifying {} actors for incidentId={} type={}", actorIds.size(), incidentId, type);
        return Mono.when(
                actorIds.stream()
                        .map(id -> notifyActor(id, title, body, type, incidentId, facts))
                        .toList());
    }

    /**
     * Sends an incident notification to all active managers of a given agency.
     *
     * <p>
     * Uses a virtual recipient ID {@code agency:{agencyId}} which is resolved
     * by the WebSocket session manager to all currently connected agency managers.
     * </p>
     *
     * @param agencyId   UUID of the target agency
     * @param title      notification title
     * @param body       notification body
     * @param type       notification type string
     * @param incidentId UUID of the related incident
     * @return Mono completing when the agency-level broadcast is dispatched
     */
    @Override
    public Mono<Void> notifyAgency(UUID agencyId, String title, String body,
            String type, UUID incidentId) {
        return notifyAgency(agencyId, title, body, type, incidentId, Map.of());
    }

    @Override
    public Mono<Void> notifyAgency(UUID agencyId, String title, String body,
            String type, UUID incidentId, Map<String, Object> facts) {
        log.info("Notifying agency={} incidentId={} type={}", agencyId, incidentId, type);

        // Virtual agency topic — resolved by WebSocket session manager
        String agencyRecipientId = "agency:" + agencyId;

        Map<String, Object> params = new java.util.LinkedHashMap<>(
                buildParams(title, body, incidentId, type, facts));
        params.put("agencyId", agencyId.toString());
        NotificationModel model = buildModel(type, params, NotificationPriority.HIGH);

        return notificationUseCase
                .send(defaultTenantId(), defaultOrganizationId(), agencyRecipientId, agencyRecipientId, model,
                        NotificationChannel.IN_APP_WEBSOCKET)
                .doOnSuccess(n -> log.debug("Agency notification sent agency={} type={}", agencyId, type))
                .doOnError(ex -> log.error("Failed to notify agency={}: {}", agencyId, ex.getMessage()))
                .onErrorResume(ex -> Mono.empty())
                .then();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * The facts of the message (reference, level, severity...) plus the legacy {@code title}/{@code body}
     * sentences. The catalog text uses the facts, so each recipient reads it in their own language; the
     * legacy sentences only serve a catalog entry that has not been rewritten yet.
     */
    private Map<String, Object> buildParams(String title, String body,
            UUID incidentId, String type, Map<String, Object> facts) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        if (facts != null) {
            params.putAll(facts);
        }
        params.put("title", title);
        params.put("body", body);
        params.put("incidentId", incidentId.toString());
        params.put("type", type);
        return params;
    }

    /**
     * Builds a {@link NotificationModel} from the incident notification type.
     * Template key convention:
     * {@code incident.notification.{type.lowercase.dotted}}.
     *
     * @param type     notification type string
     * @param params   template substitution parameters
     * @param priority delivery priority
     * @return configured NotificationModel
     */
    private NotificationModel buildModel(String type, Map<String, Object> params,
            NotificationPriority priority) {
        String templateKey = "incident.notification." + type.toLowerCase().replace("_", ".");
        return new NotificationModel(templateKey, SupportedLanguage.FR_CM.getTag(), params, priority);
    }
}
