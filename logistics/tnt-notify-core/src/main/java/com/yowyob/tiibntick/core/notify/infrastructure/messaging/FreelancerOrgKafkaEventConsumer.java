package com.yowyob.tiibntick.core.notify.infrastructure.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationPriority;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationType;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
 * <li>{@code tnt.delivery.freelancer_org.assigned} — FreelancerOrg assigned to
 * delivery</li>
 * <li>{@code tnt.billing.template.applied} — Billing template applied</li>
 * </ul>
 *
 * <p>
 * All operations are non-blocking (reactive). Errors are logged but not
 * re-thrown
 * to avoid consumer group rebalances for transient notification failures.
 *
 * @author MANFOUO Braun
 */
@Component
public class FreelancerOrgKafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(FreelancerOrgKafkaEventConsumer.class);

    private final ISendNotificationUseCase notificationUseCase;
    private final ObjectMapper objectMapper;
    private final NotifyProperties properties;

    public FreelancerOrgKafkaEventConsumer(ISendNotificationUseCase notificationUseCase,
            @Qualifier("tntObjectMapper") ObjectMapper objectMapper,
            NotifyProperties properties) {
        this.notificationUseCase = notificationUseCase;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    // ── KYC Approved (FreelancerOrg verified or level upgraded) ──────────────

    /**
     * Handles KYC approval events for FreelancerOrg.
     * Notifies the OWNER via Push + SMS + Email.
     * If the KYC_LEVEL is FULL, sends KYC_LEVEL_UPGRADED; otherwise
     * FREELANCER_ORG_VERIFIED.
     */
    @KafkaListener(topics = "tnt.admin.freelancer_org.kyc_approved", groupId = "${spring.kafka.consumer.group-id:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
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
                    templateKey, "fr",
                    Map.of("orgId", nullSafe(orgId), "kycLevel", nullSafe(kycLevel)),
                    NotificationPriority.HIGH);

            // Multi-channel: Push + SMS + Email
            return notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.PUSH_FCM)
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.SMS_LOCAL))
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.EMAIL));
        });
    }

    // ── KYC Rejected ─────────────────────────────────────────────────────────

    @KafkaListener(topics = "tnt.admin.freelancer_org.kyc_rejected", groupId = "${spring.kafka.consumer.group-id:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleKycRejected(ConsumerRecord<String, String> record) {
        log.info("Received tnt.admin.freelancer_org.kyc_rejected key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String orgId = jsonText(json, "orgId");
            String reason = jsonText(json, "reason");
            NotificationModel model = new NotificationModel(
                    "notify.freelancer_org.kyc_rejected", "fr",
                    Map.of("orgId", nullSafe(orgId), "reason", nullSafe(reason)),
                    NotificationPriority.HIGH);
            return notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.PUSH_FCM)
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.SMS_LOCAL))
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.EMAIL));
        });
    }

    // ── FreelancerOrg Suspended ───────────────────────────────────────────────

    @KafkaListener(topics = "tnt.admin.freelancer_org.suspended", groupId = "${spring.kafka.consumer.group-id:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleOrgSuspended(ConsumerRecord<String, String> record) {
        log.info("Received tnt.admin.freelancer_org.suspended key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String orgId = jsonText(json, "orgId");
            String reason = jsonText(json, "reason");
            NotificationModel model = new NotificationModel(
                    "notify.freelancer_org.suspended", "fr",
                    Map.of("orgId", nullSafe(orgId), "reason", nullSafe(reason)),
                    NotificationPriority.HIGH);
            return notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.PUSH_FCM)
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.SMS_LOCAL))
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.EMAIL));
        });
    }

    // ── FreelancerOrg Unsuspended ─────────────────────────────────────────────

    @KafkaListener(topics = "tnt.admin.freelancer_org.unsuspended", groupId = "${spring.kafka.consumer.group-id:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleOrgUnsuspended(ConsumerRecord<String, String> record) {
        log.info("Received tnt.admin.freelancer_org.unsuspended key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String orgId = jsonText(json, "orgId");
            NotificationModel model = new NotificationModel(
                    "notify.freelancer_org.unsuspended", "fr",
                    Map.of("orgId", nullSafe(orgId)),
                    NotificationPriority.NORMAL);
            return notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.PUSH_FCM)
                    .then(notificationUseCase.send(tenantId, organizationId, orgId, orgId, model, NotificationChannel.SMS_LOCAL));
        });
    }

    // ── FreelancerOrg Assigned to Delivery ───────────────────────────────────

    /**
     * Handles FreelancerOrg assignment to a delivery.
     * Sends SUB_DELIVERER_MISSION_ASSIGNED to the sub-deliverer if applicable.
     */
    @KafkaListener(topics = "tnt.delivery.freelancer_org.assigned", groupId = "${spring.kafka.consumer.group-id:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleFreelancerOrgAssigned(ConsumerRecord<String, String> record) {
        log.info("Received tnt.delivery.freelancer_org.assigned key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String deliveryId = jsonText(json, "deliveryId");
            String freelancerOrgId = jsonText(json, "freelancerOrgId");
            String freelancerRole = jsonText(json, "freelancerRole");

            // Notify the org about mission assignment
            NotificationModel modelOrg = new NotificationModel(
                    "notify.freelancer_org.mission_assigned", "fr",
                    Map.of("deliveryId", nullSafe(deliveryId),
                            "freelancerOrgId", nullSafe(freelancerOrgId)),
                    NotificationPriority.HIGH);

            Mono<?> orgNotif = notificationUseCase.send(
                    tenantId, organizationId, freelancerOrgId, freelancerOrgId, modelOrg, NotificationChannel.PUSH_FCM);

            // If SUB_DELIVERER is assigned, notify them specifically
            if ("SUB_DELIVERER".equals(freelancerRole)) {
                String subDelivererId = jsonText(json, "deliveryPersonId");
                if (subDelivererId != null && !subDelivererId.isBlank()) {
                    NotificationModel modelSub = new NotificationModel(
                            "notify.sub_deliverer.mission_assigned", "fr",
                            Map.of("deliveryId", nullSafe(deliveryId),
                                    "orgId", nullSafe(freelancerOrgId)),
                            NotificationPriority.HIGH);
                    return orgNotif
                            .then(notificationUseCase.send(
                                    tenantId, organizationId, subDelivererId, subDelivererId, modelSub, NotificationChannel.PUSH_FCM))
                            .then(notificationUseCase.send(
                                    tenantId, organizationId, subDelivererId, subDelivererId, modelSub, NotificationChannel.SMS_LOCAL));
                }
            }
            return orgNotif;
        });
    }

    // ── Billing Template Applied ──────────────────────────────────────────────

    @KafkaListener(topics = "tnt.billing.template.applied", groupId = "${spring.kafka.consumer.group-id:tnt-notify-core}", containerFactory = "notifyKafkaListenerContainerFactory")
    public void handleBillingTemplateApplied(ConsumerRecord<String, String> record) {
        log.info("Received tnt.billing.template.applied key={}", record.key());
        parseAndDispatch(record.value(), (json) -> {
            String tenantId = resolveTenantId(json);
            String organizationId = resolveOrganizationId(json);
            String actorId = jsonText(json, "actorId");
            String templateCode = jsonText(json, "templateCode");
            String policyId = jsonText(json, "policyId");
            NotificationModel model = new NotificationModel(
                    "notify.billing.template_applied", "fr",
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

    private void parseAndDispatch(String payload, NotifSupplier supplier) {
        Mono.fromCallable(() -> objectMapper.readTree(payload))
                .flatMap(json -> {
                    try {
                        return supplier.supply(json);
                    } catch (Exception e) {
                        log.warn("Error dispatching FreelancerOrg notification: {}", e.getMessage());
                        return Mono.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.error("Failed to process FreelancerOrg notification event: {}", e.getMessage());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
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

    private String nullSafe(String val) {
        return val != null ? val : "";
    }
}
