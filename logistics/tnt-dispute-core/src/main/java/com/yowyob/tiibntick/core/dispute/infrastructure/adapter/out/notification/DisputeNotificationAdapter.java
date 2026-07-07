package com.yowyob.tiibntick.core.dispute.infrastructure.adapter.out.notification;

import com.yowyob.tiibntick.core.dispute.application.port.outbound.IDisputeNotificationPort;
import com.yowyob.tiibntick.core.dispute.domain.model.Dispute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class DisputeNotificationAdapter implements IDisputeNotificationPort {

    private static final Logger log = LoggerFactory.getLogger(DisputeNotificationAdapter.class);

    @Override
    public Mono<Void> notifyDisputeOpened(Dispute dispute) {
        return Mono.fromRunnable(() ->
                log.info("[NOTIFY] Dispute opened: ref={} claimant={} priority={}",
                        dispute.getReference().getValue(),
                        dispute.getClaimantId(),
                        dispute.getPriority()));
    }

    @Override
    public Mono<Void> notifyMediatorAssigned(Dispute dispute) {
        return Mono.fromRunnable(() ->
                log.info("[NOTIFY] Mediator assigned: ref={} mediator={}",
                        dispute.getReference().getValue(),
                        dispute.getAssignedMediatorId()));
    }

    @Override
    public Mono<Void> notifyRulingIssued(Dispute dispute) {
        return Mono.fromRunnable(() ->
                log.info("[NOTIFY] Ruling issued: ref={} resolution={}",
                        dispute.getReference().getValue(),
                        dispute.getResolution() != null ? dispute.getResolution().getType() : "N/A"));
    }

    @Override
    public Mono<Void> notifyCompensationPaid(Dispute dispute) {
        return Mono.fromRunnable(() ->
                log.info("[NOTIFY] Compensation paid: ref={} amount={}",
                        dispute.getReference().getValue(),
                        dispute.getCompensation() != null ? dispute.getCompensation().formattedAmount() : "N/A"));
    }

    @Override
    public Mono<Void> notifyDisputeClosed(Dispute dispute) {
        return Mono.fromRunnable(() ->
                log.info("[NOTIFY] Dispute closed: ref={}", dispute.getReference().getValue()));
    }

    @Override
    public Mono<Void> notifySlaBreached(Dispute dispute, String breachType) {
        return Mono.fromRunnable(() ->
                log.warn("[NOTIFY] SLA breached: ref={} breachType={}", dispute.getReference().getValue(), breachType));
    }
}
