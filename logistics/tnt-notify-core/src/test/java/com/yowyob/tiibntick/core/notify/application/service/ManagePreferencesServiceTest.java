package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationPreferencePort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.EnumSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GererPreferencesService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class ManagePreferencesServiceTest {

    @Mock
    private INotificationPreferencePort preferencePort;

    private ManagePreferencesService service;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setUp() {
        service = new ManagePreferencesService(preferencePort);
    }

    @Test
    void getPreferences_shouldCreateDefaults_whenUserHasNone() {
        when(preferencePort.findByUserId(USER_ID)).thenReturn(Mono.empty());
        when(preferencePort.save(any())).thenAnswer(inv ->
                Mono.just(inv.getArgument(0, NotificationPreference.class)));

        StepVerifier.create(service.getPreferences(USER_ID))
                .expectNextMatches(p -> p.getUserId().equals(USER_ID)
                        && p.areNotificationsEnabled()
                        && !p.getActiveChannels().isEmpty())
                .verifyComplete();
    }

    @Test
    void disableChannel_shouldRemoveCanalAndSave() {
        NotificationPreference existing = new NotificationPreference(
                USER_ID, EnumSet.allOf(NotificationChannel.class), "fr_CM");
        when(preferencePort.findByUserId(USER_ID)).thenReturn(Mono.just(existing));
        when(preferencePort.save(any())).thenAnswer(inv ->
                Mono.just(inv.getArgument(0, NotificationPreference.class)));

        StepVerifier.create(service.disableChannel(USER_ID, NotificationChannel.WHATSAPP))
                .expectNextMatches(p -> !p.acceptsChannel(NotificationChannel.WHATSAPP))
                .verifyComplete();
    }

    @Test
    void enableChannel_shouldAddCanalAndSave() {
        NotificationPreference existing = new NotificationPreference(
                USER_ID, EnumSet.of(NotificationChannel.SMS_LOCAL), "en_CM");
        when(preferencePort.findByUserId(USER_ID)).thenReturn(Mono.just(existing));
        when(preferencePort.save(any())).thenAnswer(inv ->
                Mono.just(inv.getArgument(0, NotificationPreference.class)));

        StepVerifier.create(service.enableChannel(USER_ID, NotificationChannel.PUSH_FCM))
                .expectNextMatches(p -> p.acceptsChannel(NotificationChannel.PUSH_FCM))
                .verifyComplete();
    }

    @Test
    void changeLanguage_shouldUpdateLocaleAndSave() {
        NotificationPreference existing = new NotificationPreference(
                USER_ID, EnumSet.allOf(NotificationChannel.class), "fr_CM");
        when(preferencePort.findByUserId(USER_ID)).thenReturn(Mono.just(existing));
        when(preferencePort.save(any())).thenAnswer(inv ->
                Mono.just(inv.getArgument(0, NotificationPreference.class)));

        StepVerifier.create(service.changeLanguage(USER_ID, "en_CM"))
                .expectNextMatches(p -> "en_CM".equals(p.getPreferredLanguage()))
                .verifyComplete();
    }
}
