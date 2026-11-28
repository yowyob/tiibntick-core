package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.yowyob.tiibntick.core.notify.domain.model.FreelancerOrgNotificationTemplates;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.kernel.i18n.domain.enums.SupportedLanguage;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationType;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Kafka consumer for FreelancerOrganization lifecycle events.
 *
 * <p>
 * Listens to admin and organization events from tnt-administration-core and
 * tnt-organization-core, translates them into TiiBnTick notifications, and
 * dispatches
 * them via the appropriate channels (Push, SMS, Email).
 * </p>
 *
 * <p>
 * Consumed topics:
 * <ul>
 * <li>{@code tnt.admin.freelancer_org.kyc_approved} — KYC approved / level
 * upgraded</li>
 * <li>{@code tnt.admin.freelancer_org.kyc_rejected} — KYC rejected</li>
 * <li>{@code tnt.admin.freelancer_org.suspended} — FreelancerOrg suspended</li>
 * <li>{@code tnt.admin.freelancer_org.unsuspended} — FreelancerOrg
 * unsuspended</li>
 * <li>{@code DeliveryFreelancerOrgAssigned} of {@code tnt.delivery.events} (lot L8, was
 * {@code tnt.delivery.freelancer_org.assigned}) — FreelancerOrg assigned to delivery</li>
 * <li>{@code tnt.billing.template.applied} — Billing template applied</li>
 * </ul>
 *
 * <p>
 * Each listener awaits its notification work (lot L7); a failure is retried, then dead-lettered to
 * {@code tnt.dlq}.
 *
 * @author MANFOUO Braun
 */
@Component
public class FreelancerOrgKafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FreelancerOrgKafkaEventConsumer.class);

    private final ISendNotificationUseCase notificationUseCase;
    private final ObjectMapper objectMapper;
    private final NotifyProperties properties;
    private final ReactiveListenerSupport listenerSupport;

    public FreelancerOrgKafkaEventConsumer(ISendNotificationUseCase notificationUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper,
            NotifyProperties properties,
            ReactiveListenerSupport listenerSupport) {
        this.notificationUseCase = notificationUseCase;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.listenerSupport = listenerSupport;
    }

    // ── KYC Approved (FreelancerOrg verified or level upgraded) ──────────────

    /**
     * Handles KYC approval events for FreelancerOrg.
     * Notifies the OWNER via Push + SMS + Email.
     * If the KYC_LEVEL is FULL, sends KYC_LEVEL_UPGRADED; otherwise
     * FREELANCER_ORG_VERIFIED.
     */
    @KafkaListener(topics = com.yowyob.tiibntick.common.kafka.TntTopics.ADMIN_FREELANCER_ORG_KYC_APPROVED, groupId = "${tnt.kafka.consumer.groups.notify:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleKycApproved(ConsumerRecord<String, String> record) {
        log.info("Received tnt.admin.freelancer_org.kyc_approved key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String orgId = jsonText(json, "orgId");
            //String ownerActorId = jsonText(json, "adminId"); // admin who approved — target is owner
            String kycLevel = jsonText(json, "KYC_LEVEL");

            NotificationType type = "FULL".equals(kycLevel)
                    ? NotificationType.KYC_LEVEL_UPGRADED
                    : NotificationType.FREELANCER_ORG_VERIFIED;

            String templateKey = "FULL".equals(kycLevel)
                    ? "notify.freelancer_org.kyc_level_upgraded"
                    : "notify.freelancer_org.verified";

            // Notify OWNER — we use orgId as recipientId (resolved to ownerActorId by
            // preference layer)
            NotificationModel model = new NotificationModel(
                    templateKey, SupportedLanguage.FR_CM.getTag(),
                    Map.of("orgId", nullSafe(orgId), "kycLevel", nullSafe(kycLevel)),
                    NotificationPriority.HIGH);

            // Multi-channel: Push + SMS + Email
            return notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.PUSH_FCM)
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.SMS_LOCAL))
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.EMAIL));
        });
    }

    // ── KYC Rejected ─────────────────────────────────────────────────────────

    @KafkaListener(topics = com.yowyob.tiibntick.common.kafka.TntTopics.ADMIN_FREELANCER_ORG_KYC_REJECTED, groupId = "${tnt.kafka.consumer.groups.notify:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleKycRejected(ConsumerRecord<String, String> record) {
        log.info("Received tnt.admin.freelancer_org.kyc_rejected key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String orgId = jsonText(json, "orgId");
            String reason = jsonText(json, "reason");
            NotificationModel model = new NotificationModel(
                    "notify.freelancer_org.kyc_rejected", SupportedLanguage.FR_CM.getTag(),
                    Map.of("orgId", nullSafe(orgId), "reason", nullSafe(reason)),
                    NotificationPriority.HIGH);
            return notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.PUSH_FCM)
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.SMS_LOCAL))
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.EMAIL));
        });
    }

    // ── FreelancerOrg Suspended ───────────────────────────────────────────────

    @KafkaListener(topics = com.yowyob.tiibntick.common.kafka.TntTopics.ADMIN_FREELANCER_ORG_SUSPENDED, groupId = "${tnt.kafka.consumer.groups.notify:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleOrgSuspended(ConsumerRecord<String, String> record) {
        log.info("Received tnt.admin.freelancer_org.suspended key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String orgId = jsonText(json, "orgId");
            String reason = jsonText(json, "reason");
            NotificationModel model = new NotificationModel(
                    "notify.freelancer_org.suspended", SupportedLanguage.FR_CM.getTag(),
                    Map.of("orgId", nullSafe(orgId), "reason", nullSafe(reason)),
                    NotificationPriority.HIGH);
            return notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.PUSH_FCM)
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.SMS_LOCAL))
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.EMAIL));
        });
    }

    // ── FreelancerOrg Unsuspended ─────────────────────────────────────────────

    @KafkaListener(topics = com.yowyob.tiibntick.common.kafka.TntTopics.ADMIN_FREELANCER_ORG_UNSUSPENDED, groupId = "${tnt.kafka.consumer.groups.notify:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleOrgUnsuspended(ConsumerRecord<String, String> record) {
        log.info("Received tnt.admin.freelancer_org.unsuspended key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String orgId = jsonText(json, "orgId");
            NotificationModel model = new NotificationModel(
                    "notify.freelancer_org.unsuspended", SupportedLanguage.FR_CM.getTag(),
                    Map.of("orgId", nullSafe(orgId)),
                    NotificationPriority.NORMAL);
            return notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.PUSH_FCM)
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.SMS_LOCAL));
        });
    }

    // ── FreelancerOrg Assigned to Delivery ───────────────────────────────────

    static final String FREELANCER_ORG_ASSIGNED = "DeliveryFreelancerOrgAssigned";

    /**
     * Handles FreelancerOrg assignment to a delivery ({@code DeliveryFreelancerOrgAssigned}; other event types and
     * legacy records are ignored). Notifies the org; when the role is SUB_DELIVERER and the delivery has a courier,
     * the sub-deliverer too.
     */
    @KafkaListener(topics = com.yowyob.tiibntick.common.kafka.TntTopics.DELIVERY_EVENTS, groupId = "${tnt.kafka.consumer.groups.notify:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleFreelancerOrgAssigned(ConsumerRecord<String, String> record) throws Exception {
        JsonNode event = objectMapper.readTree(record.value());
        if (!com.yowyob.tiibntick.common.kafka.TntAggregateEvent.isAggregateEvent(event)
                || !FREELANCER_ORG_ASSIGNED.equals(event.path("eventType").asText())) {
            return;
        }
        log.info("Received DeliveryFreelancerOrgAssigned key={}", record.key());
        JsonNode delivery = event.path("payload");
        String tenantId = resolveTenantId(event);
        String organizationId = resolveOrganizationId(event);
        String deliveryId = event.path("aggregateId").asText();
        String freelancerOrgId = jsonText(delivery, "freelancerOrgId");
        String freelancerRole = jsonText(event.path("details"), "role");

        // Notify the org about the delivery assignment
        NotificationModel modelOrg = new NotificationModel(
                FreelancerOrgNotificationTemplates.FREELANCER_ORG_DELIVERY_ASSIGNED, SupportedLanguage.FR_CM.getTag(),
                Map.of("deliveryId", nullSafe(deliveryId),
                        "freelancerOrgId", nullSafe(freelancerOrgId)),
                NotificationPriority.HIGH);
        Mono<?> orgNotif = notificationUseCase.send(
                tenantId, organizationId, freelancerOrgId, freelancerOrgId, modelOrg, NotificationChannel.PUSH_FCM);

        // If SUB_DELIVERER is assigned, notify them specifically
        String subDelivererId = jsonText(delivery, "courierId");
        if ("SUB_DELIVERER".equals(freelancerRole) && subDelivererId != null && !subDelivererId.isBlank()) {
            NotificationModel modelSub = new NotificationModel(
                    FreelancerOrgNotificationTemplates.SUB_DELIVERER_DELIVERY_ASSIGNED, SupportedLanguage.FR_CM.getTag(),
                    Map.of("deliveryId", nullSafe(deliveryId),
                            "orgId", nullSafe(freelancerOrgId)),
                    NotificationPriority.HIGH);
            orgNotif = orgNotif
                    .then(notificationUseCase.send(
                            tenantId, organizationId, subDelivererId, subDelivererId, modelSub, NotificationChannel.PUSH_FCM))
                    .then(notificationUseCase.send(
                            tenantId, organizationId, subDelivererId, subDelivererId, modelSub, NotificationChannel.SMS_LOCAL));
        }
        listenerSupport.await(orgNotif.then());
    }

    // ── Billing Template Applied ──────────────────────────────────────────────

    @KafkaListener(topics = com.yowyob.tiibntick.common.kafka.TntTopics.BILLING_TEMPLATE_APPLIED, groupId = "${tnt.kafka.consumer.groups.notify:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleBillingTemplateApplied(ConsumerRecord<String, String> record) {
        log.info("Received tnt.billing.template.applied key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            // TemplateAppliedEvent (tnt-billing-templates, via the outbox) names them ownerActorId /
            // createdPolicyId; actorId / policyId are kept for any producer using the short names.
            String actorId = firstNonNull(jsonText(json, "actorId"), jsonText(json, "ownerActorId"));
            String templateCode = jsonText(json, "templateCode");
            String policyId = firstNonNull(jsonText(json, "policyId"), jsonText(json, "createdPolicyId"));
            NotificationModel model = new NotificationModel(
                    "notify.billing.template_applied", SupportedLanguage.FR_CM.getTag(),
                    Map.of("actorId", nullSafe(actorId),
                            "templateCode", nullSafe(templateCode),
                            "policyId", nullSafe(policyId)),
                    NotificationPriority.NORMAL);
            return notificationUseCase.send(tenantId, organizationId, actorId, actorId, model, NotificationChannel.IN_APP_WEBSOCKET);
        });
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @FunctionalInterface
    private interface NotifSupplier {
        Mono<?> supply(JsonNode json) throws Exception;
    }

    /**
     * Lot L7 (spec §3.10): the notification work is awaited on the container thread; an unreadable record or a
     * failed send reaches the container (retries, then {@code tnt.dlq}) instead of being logged and lost.
     * Delivery is at least once: a retry after a partial failure may resend a channel that had succeeded.
     */
    private void parseAndDispatch(String payload, NotifSupplier supplier) {
        listenerSupport.await(Mono.fromCallable(
                        () -> com.yowyob.tiibntick.common.kafka.EventPayloads.parse(objectMapper, payload))
                .flatMap(json -> Mono.defer(() -> {
                    try {
                        return supplier.supply(json);
                    } catch (Exception e) {
                        return Mono.error(e);
                    }
                })));
    }

    private String jsonText(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return (node != null && !node.isNull()) ? node.asText() : null;
    }

    /**
     * Reads the publishing module's {@code tenantId} field off the event
     * envelope, falling back to {@code tnt.notify.kernel.default-tenant-id}
     * when absent — this Kafka consumer has no HTTP request/JWT to read
     * X-Tenant-Id from, and the Kernel notification engine requires one.
     */
    private String resolveTenantId(JsonNode json) {
        String tenantId = jsonText(json, "tenantId");
        return tenantId != null ? tenantId : properties.getKernel().getDefaultTenantId();
    }

    private String resolveOrganizationId(JsonNode json) {
        String organizationId = jsonText(json, "organizationId");
        return organizationId != null ? organizationId : properties.getKernel().getDefaultOrganizationId();
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private String nullSafe(String val) {
        return val != null ? val : "";
    }
}
