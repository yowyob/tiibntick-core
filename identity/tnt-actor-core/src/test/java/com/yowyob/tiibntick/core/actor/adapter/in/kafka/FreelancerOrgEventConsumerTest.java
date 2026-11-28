package com.yowyob.tiibntick.core.actor.adapter.in.kafka;

import com.yowyob.tiibntick.common.kafka.ReactiveListenerSupport;
import com.yowyob.tiibntick.core.actor.application.command.LinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.command.UnlinkFreelancerOrgCommand;
import com.yowyob.tiibntick.core.actor.application.port.in.ILinkFreelancerOrgUseCase;
import com.yowyob.tiibntick.core.actor.domain.model.FreelancerRole;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lot L7: the four listeners await their work; invalid records and failures reach the container. */
class FreelancerOrgEventConsumerTest {

    private final ILinkFreelancerOrgUseCase links = mock(ILinkFreelancerOrgUseCase.class);
    private final FreelancerOrgEventConsumer consumer =
            new FreelancerOrgEventConsumer(links, ReactiveListenerSupport.withDefaultTimeout());

    private static ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("t", 0, 0L, "k", json);
    }

    @Test
    void linksTheOwnerSynchronously() {
        UUID org = UUID.randomUUID(), owner = UUID.randomUUID(), tenant = UUID.randomUUID();
        when(links.linkToFreelancerOrg(any())).thenReturn(Mono.empty());

        consumer.onFreelancerOrgCreated(record("{\"orgId\":\"" + org + "\",\"ownerActorId\":\"" + owner
                + "\",\"tenantId\":\"" + tenant + "\"}"));

        ArgumentCaptor<LinkFreelancerOrgCommand> cmd = ArgumentCaptor.forClass(LinkFreelancerOrgCommand.class);
        verify(links).linkToFreelancerOrg(cmd.capture());
        assertThat(cmd.getValue().role()).isEqualTo(FreelancerRole.OWNER);
        assertThat(cmd.getValue().actorId()).isEqualTo(owner);
    }

    @Test
    void linksAndUnlinksSubDeliverersAndUpdatesVerification() {
        UUID org = UUID.randomUUID(), sub = UUID.randomUUID(), tenant = UUID.randomUUID();
        when(links.linkToFreelancerOrg(any())).thenReturn(Mono.empty());
        when(links.unlinkFromFreelancerOrg(any())).thenReturn(Mono.empty());
        when(links.updateOrgVerificationStatus(org, true)).thenReturn(Mono.empty());
        String json = "{\"orgId\":\"" + org + "\",\"subDelivererId\":\"" + sub + "\",\"tenantId\":\"" + tenant
                + "\",\"isOrgVerified\":true}";

        consumer.onSubDelivererAssociated(record(json));
        consumer.onSubDelivererRevoked(record(json));
        consumer.onFreelancerOrgVerified(record("{\"orgId\":\"" + org + "\"}"));

        verify(links).linkToFreelancerOrg(any(LinkFreelancerOrgCommand.class));
        verify(links).unlinkFromFreelancerOrg(any(UnlinkFreelancerOrgCommand.class));
        verify(links).updateOrgVerificationStatus(org, true);
    }

    @Test
    void invalidRecordsAndFailuresReachTheContainer() {
        assertThatThrownBy(() -> consumer.onFreelancerOrgCreated(record("{\"orgId\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> consumer.onFreelancerOrgVerified(record("not json"))).isNotNull();
        assertThatThrownBy(() -> consumer.onSubDelivererRevoked(record("{}")))
                .isInstanceOf(IllegalArgumentException.class);
        verify(links, never()).linkToFreelancerOrg(any());

        UUID org = UUID.randomUUID();
        when(links.updateOrgVerificationStatus(org, true)).thenReturn(Mono.error(new IllegalStateException("db")));
        assertThatThrownBy(() -> consumer.onFreelancerOrgVerified(record("{\"orgId\":\"" + org + "\"}")))
                .hasMessageContaining("db");
    }
}
