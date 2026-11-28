package com.yowyob.tiibntick.bootstrap.bridge;

import com.yowyob.tiibntick.core.notify.application.port.in.IInAppInboxUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformGrantKind;
import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Decision D-172 and the maintainer directive of 2026-11-27: e-mail to beneficiary and owner, in-app message to the owner, never blocking. */
class PlatformAdminAlertsTest {

    final UUID owner = UUID.randomUUID();
    final UUID alice = UUID.randomUUID();
    final ISendNotificationUseCase mail = mock(ISendNotificationUseCase.class);
    final IInAppInboxUseCase inbox = mock(IInAppInboxUseCase.class);

    @SuppressWarnings("unchecked")
    static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> p = mock(ObjectProvider.class);
        when(p.getIfAvailable()).thenReturn(bean);
        return p;
    }

    PlatformAdminAlerts alerts() {
        return new PlatformAdminAlerts(provider(mail), provider(inbox));
    }

    PlatformRoleGrant grant() {
        return PlatformRoleGrant.newGrant(alice, PlatformGrantKind.ASSIGNED, owner, UUID.randomUUID(), UUID.randomUUID(),
                Instant.now());
    }

    @Test
    void grantMailsBothAndWritesTheOwnersInboxWithoutForcingALanguage() {
        when(mail.send(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(inbox.sendLocalized(any(), any(), any(), anyMap(), any(), anyMap())).thenReturn(Mono.empty());
        PlatformRoleGrant g = grant();

        StepVerifier.create(alerts().granted(g, owner)).verifyComplete();

        ArgumentCaptor<NotificationModel> model = ArgumentCaptor.forClass(NotificationModel.class);
        verify(mail).send(any(), isNull(), eq(alice.toString()), isNull(), model.capture(), eq(NotificationChannel.EMAIL));
        verify(mail).send(any(), isNull(), eq(owner.toString()), isNull(), any(), eq(NotificationChannel.EMAIL));
        assertThat(model.getValue().templateKey()).isEqualTo(PlatformAdminAlerts.GRANTED);
        assertThat(model.getValue().targetLanguage()).as("each account's own language is resolved by notify").isNull();
        verify(inbox).sendLocalized(eq(owner), eq(PlatformAdminAlerts.INBOX_APPLICATION), eq(PlatformAdminAlerts.GRANTED),
                eq(PlatformAdminAlerts.params(g)), isNull(), eq(Map.of("grantId", g.id().toString(),
                        "event", PlatformAdminAlerts.GRANTED)));
        verify(inbox, never()).sendLocalized(eq(alice), any(), any(), anyMap(), any(), anyMap());
    }

    @Test
    void theOwnerAsBeneficiaryGetsOneMailAndOneMessage() {
        when(mail.send(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        when(inbox.sendLocalized(any(), any(), any(), anyMap(), any(), anyMap())).thenReturn(Mono.empty());
        PlatformRoleGrant own = PlatformRoleGrant.newGrant(owner, PlatformGrantKind.SYSTEM_OWNER, owner, UUID.randomUUID(),
                UUID.randomUUID(), Instant.now());
        StepVerifier.create(alerts().granted(own, owner)).verifyComplete();
        verify(mail, times(1)).send(any(), any(), any(), any(), any(), any());
        verify(inbox, times(1)).sendLocalized(any(), any(), any(), anyMap(), any(), anyMap());
    }

    @Test
    void failuresNeverPropagateAndTheInboxIsStillTriedAfterAMailFailure() {
        when(mail.send(any(), any(), any(), any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("kernel")));
        when(inbox.sendLocalized(any(), any(), any(), anyMap(), any(), anyMap()))
                .thenReturn(Mono.error(new IllegalStateException("kernel")));
        StepVerifier.create(alerts().revoked(grant(), owner)).verifyComplete();
        verify(inbox).sendLocalized(eq(owner), any(), eq(PlatformAdminAlerts.REVOKED), anyMap(), isNull(), anyMap());
    }

    @Test
    void withoutARecordedOwnerOnlyTheBeneficiaryIsMailed() {
        when(mail.send(any(), any(), any(), any(), any(), any())).thenReturn(Mono.empty());
        StepVerifier.create(alerts().granted(grant(), null)).verifyComplete();
        verify(mail, times(1)).send(any(), any(), eq(alice.toString()), any(), any(), any());
        verify(inbox, never()).sendLocalized(any(), any(), any(), anyMap(), any(), anyMap());
    }
}
