package com.yowyob.tiibntick.core.actor.adapter.in.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.common.kafka.TntTopics;
import com.yowyob.tiibntick.core.actor.application.command.LinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.command.UnlinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ILinkFreelancerOrgUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerRole;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Kafka consumer that synchronizes {@code tnt-actor-core} FreelancerProfile data
 * in response to FreelancerOrganization events emitted by {@code tnt-organization-core}.
 *
 * <h3>Topics consumed</h3>
 * <ul>
 *   <li>{@code tnt.freelancer_org.created} — links the OWNER actor to the new org.</li>
 *   <li>{@code tnt.freelancer_org.sub_deliverer.associated} — links a sub-deliverer actor.</li>
 *   <li>{@code tnt.freelancer_org.sub_deliverer.revoked} — unlinks a sub-deliverer actor.</li>
 *   <li>{@code tnt.freelancer_org.verified} — updates cached {@code isOrgVerified} for
 *       all profiles (OWNER + sub-deliverers) linked to the verified org.</li>
 *   <li>{@code tnt.freelancer_org.suspended} — bulk-suspends all actors of the org
 *       by clearing their operational status.</li>
 * </ul>
 *
 * <h3>Design</h3>
 * <p>This consumer follows the same lightweight payload extraction pattern as
 * {@link IncidentEventConsumer}. It reads only the fields it needs from the JSON
 * payload without depending on any {@code tnt-organization-core} domain classes.
 * This preserves module independence (no direct import of org-module classes).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
public class FreelancerOrgEventConsumer {
    // Lot L7 (spec §3.10): every listener awaits its work on the container thread (ReactiveListenerSupport); an
    // unreadable record, a missing field or a processing error reaches the container: retries, then tnt.dlq.
    // Linking/unlinking is idempotent in ILinkFreelancerOrgUseCase, so a replay has no extra effect.

    private final ILinkFreelancerOrgUseCase linkUseCase;
    private final ObjectMapper objectMapper;
    private final ReactiveListenerSupport listenerSupport;

    public FreelancerOrgEventConsumer(ILinkFreelancerOrgUseCase linkUseCase, ReactiveListenerSupport listenerSupport) {
        this.linkUseCase = linkUseCase;
        this.objectMapper = new ObjectMapper();
        this.listenerSupport = listenerSupport;
    }

    // ── tnt.freelancer_org.created ────────────────────────────────────────────

    /**
     * When a new FreelancerOrganization is created, links the OWNER actor to it.
     *
     * <p>Expected payload fields:
     * <pre>{@code
     * {
     *   "orgId":        "<UUID>",
     *   "tenantId":     "<UUID — org's tenant (FRL-…)>",
     *   "ownerActorId": "<UUID>"
     * }
     * }</pre>
     *
     * @param record the Kafka consumer record
     */
    @KafkaListener(
            topics = TntTopics.FREELANCER_ORG_CREATED,
            groupId = "${tnt.kafka.consumer.groups.actor-freelancer-org:tnt-actor-freelancer-org-consumer}",
            containerFactory = "actorKafkaListenerContainerFactory")
    public void onFreelancerOrgCreated(ConsumerRecord<String, String> record) {
        log.debug("Received tnt.freelancer_org.created: key={}", record.key());
        listenerSupport.await(Mono.fromCallable(() -> objectMapper.readTree(record.value()))
                .flatMap(payload -> {
                    UUID orgId        = extractUuid(payload, "orgId");
                    UUID ownerActorId = extractUuid(payload, "ownerActorId");
                    UUID tenantId     = extractUuid(payload, "tenantId");

                    if (orgId == null || ownerActorId == null || tenantId == null) {
                        return Mono.error(new IllegalArgumentException("tnt.freelancer_org.created: missing required fields in payload"));
                    }
                    LinkFreelancerOrgCommand cmd = new LinkFreelancerOrgCommand(
                            tenantId, ownerActorId, orgId, FreelancerRole.OWNER, false);
                    return linkUseCase.linkToFreelancerOrg(cmd);
                }));
    }

    // ── tnt.freelancer_org.sub_deliverer.associated ───────────────────────────

    /**
     * When a sub-deliverer accepts an invitation, links their actor profile to the org.
     *
     * <p>Expected payload fields:
     * <pre>{@code
     * {
     *   "orgId":          "<UUID>",
     *   "tenantId":       "<UUID — sub-deliverer's tenant>",
     *   "subDelivererId": "<UUID>"
     * }
     * }</pre>
     *
     * @param record the Kafka consumer record
     */
    @KafkaListener(
            topics = TntTopics.FREELANCER_ORG_SUB_DELIVERER_ASSOCIATED,
            groupId = "${tnt.kafka.consumer.groups.actor-freelancer-org:tnt-actor-freelancer-org-consumer}",
            containerFactory = "actorKafkaListenerContainerFactory")
    public void onSubDelivererAssociated(ConsumerRecord<String, String> record) {
        log.debug("Received tnt.freelancer_org.sub_deliverer.associated: key={}", record.key());
        listenerSupport.await(Mono.fromCallable(() -> objectMapper.readTree(record.value()))
                .flatMap(payload -> {
                    UUID orgId          = extractUuid(payload, "orgId");
                    UUID subDeliverId   = extractUuid(payload, "subDelivererId");
                    UUID tenantId       = extractUuid(payload, "tenantId");

                    if (orgId == null || subDeliverId == null || tenantId == null) {
                        return Mono.error(new IllegalArgumentException("tnt.freelancer_org.sub_deliverer.associated: missing fields"));
                    }
                    // org is already verified if the owner was verified before inviting
                    boolean isOrgVerified = extractBoolean(payload, "isOrgVerified");
                    LinkFreelancerOrgCommand cmd = new LinkFreelancerOrgCommand(
                            tenantId, subDeliverId, orgId, FreelancerRole.SUB_DELIVERER,
                            isOrgVerified);
                    return linkUseCase.linkToFreelancerOrg(cmd);
                }));
    }

    // ── tnt.freelancer_org.sub_deliverer.revoked ──────────────────────────────

    /**
     * When a sub-deliverer is revoked from an org, unlinks their actor profile.
     *
     * <p>Expected payload fields:
     * <pre>{@code
     * {
     *   "orgId":          "<UUID>",
     *   "tenantId":       "<UUID>",
     *   "subDelivererId": "<UUID>"
     * }
     * }</pre>
     *
     * @param record the Kafka consumer record
     */
    @KafkaListener(
            topics = TntTopics.FREELANCER_ORG_SUB_DELIVERER_REVOKED,
            groupId = "${tnt.kafka.consumer.groups.actor-freelancer-org:tnt-actor-freelancer-org-consumer}",
            containerFactory = "actorKafkaListenerContainerFactory")
    public void onSubDelivererRevoked(ConsumerRecord<String, String> record) {
        log.debug("Received tnt.freelancer_org.sub_deliverer.revoked: key={}", record.key());
        listenerSupport.await(Mono.fromCallable(() -> objectMapper.readTree(record.value()))
                .flatMap(payload -> {
                    UUID orgId        = extractUuid(payload, "orgId");
                    UUID subDeliverId = extractUuid(payload, "subDelivererId");
                    UUID tenantId     = extractUuid(payload, "tenantId");

                    if (orgId == null || subDeliverId == null || tenantId == null) {
                        return Mono.error(new IllegalArgumentException("tnt.freelancer_org.sub_deliverer.revoked: missing fields"));
                    }
                    UnlinkFreelancerOrgCommand cmd = new UnlinkFreelancerOrgCommand(
                            tenantId, subDeliverId, orgId);
                    return linkUseCase.unlinkFromFreelancerOrg(cmd);
                }));
    }

    // ── tnt.freelancer_org.verified ───────────────────────────────────────────

    /**
     * When a FreelancerOrganization is verified, updates the cached
     * {@code isOrgVerified} flag for all linked actor profiles (OWNER + subs).
     *
     * <p>Expected payload fields:
     * <pre>{@code
     * {
     *   "orgId": "<UUID>"
     * }
     * }</pre>
     *
     * @param record the Kafka consumer record
     */
    @KafkaListener(
            topics = TntTopics.FREELANCER_ORG_VERIFIED,
            groupId = "${tnt.kafka.consumer.groups.actor-freelancer-org:tnt-actor-freelancer-org-consumer}",
            containerFactory = "actorKafkaListenerContainerFactory")
    public void onFreelancerOrgVerified(ConsumerRecord<String, String> record) {
        log.debug("Received tnt.freelancer_org.verified: key={}", record.key());
        listenerSupport.await(Mono.fromCallable(() -> objectMapper.readTree(record.value()))
                .flatMap(payload -> {
                    UUID orgId = extractUuid(payload, "orgId");
                    if (orgId == null) {
                        return Mono.error(new IllegalArgumentException("tnt.freelancer_org.verified: missing orgId in payload"));
                    }
                    return linkUseCase.updateOrgVerificationStatus(orgId, true);
                }));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private UUID extractUuid(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return null;
        }
        try {
            return UUID.fromString(node.get(fieldName).asText());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID for field '{}': {}", fieldName,
                    node.get(fieldName).asText());
            return null;
        }
    }

    private boolean extractBoolean(JsonNode node, String fieldName) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return false;
        }
        return node.get(fieldName).asBoolean(false);
    }
}
