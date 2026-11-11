package com.yowyob.tiibntick.core.notify.infrastructure.adapter.profile;

import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort;
import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort.Kind;
import com.yowyob.tiibntick.core.actor.application.port.out.IProfileWelcomePort.Welcome;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.config.NotifyProperties;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.Notification;
import com.yowyob.tiibntick.core.notify.domain.vo.NotificationModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileWelcomeNotificationAdapterTest {

    private final ISendNotificationUseCase send = mock(ISendNotificationUseCase.class);
    private final ProfileWelcomeNotificationAdapter adapter =
            new ProfileWelcomeNotificationAdapter(send, new NotifyProperties());
    private final UUID tenant = UUID.randomUUID();
    private final UUID actor = UUID.randomUUID();

    private void sendsAlwaysSucceed() {
        when(send.send(any(), any(), any(), any(), any(), any())).thenReturn(Mono.just(mock(Notification.class)));
    }

    @Test
    void sendsAnOnScreenNoticeAndAnEmailForTheCreatedProfile() {
        sendsAlwaysSucceed();
        StepVerifier.create(adapter.welcome(new Welcome(tenant, actor, Kind.FREELANCER, "Jo", "jo@x.cm")))
                .verifyComplete();

        ArgumentCaptor<NotificationModel> model = ArgumentCaptor.forClass(NotificationModel.class);
        verify(send).send(eq(tenant.toString()), any(), eq(actor.toString()), eq(actor.toString()),
                model.capture(), eq(NotificationChannel.IN_APP_WEBSOCKET));
        assertThat(model.getValue().templateKey()).isEqualTo("notify.profile.welcome.freelancer");
        assertThat(model.getValue().parameters()).containsEntry("name", "Jo");

        ArgumentCaptor<NotificationModel> mail = ArgumentCaptor.forClass(NotificationModel.class);
        // the e-mail goes to the address, but the recipient id stays the account so its language is found
        verify(send).send(eq(tenant.toString()), any(), eq(actor.toString()), eq("jo@x.cm"),
                mail.capture(), eq(NotificationChannel.EMAIL));
        assertThat(mail.getValue().templateKey()).isEqualTo("notify.profile.created.freelancer");
    }

    @Test
    void withoutAnAddressOnlyTheOnScreenNoticeIsSent() {
        sendsAlwaysSucceed();
        StepVerifier.create(adapter.welcome(new Welcome(tenant, actor, Kind.CLIENT, null, null))).verifyComplete();
        verify(send).send(any(), any(), any(), any(), any(), eq(NotificationChannel.IN_APP_WEBSOCKET));
        verify(send, never()).send(any(), any(), any(), any(), any(), eq(NotificationChannel.EMAIL));
    }

    @Test
    void aFailingEmailDoesNotPreventTheOnScreenNotice() {
        when(send.send(any(), any(), any(), any(), any(), eq(NotificationChannel.EMAIL)))
                .thenReturn(Mono.error(new IllegalStateException("smtp down")));
        when(send.send(any(), any(), any(), any(), any(), eq(NotificationChannel.IN_APP_WEBSOCKET)))
                .thenReturn(Mono.just(mock(Notification.class)));
        StepVerifier.create(adapter.welcome(new Welcome(tenant, actor, Kind.DELIVERER, "Jo", "jo@x.cm")))
                .verifyComplete();
        verify(send).send(any(), any(), any(), any(), any(), eq(NotificationChannel.IN_APP_WEBSOCKET));
    }
}
