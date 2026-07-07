package com.yowyob.tiibntick.core.notify.infrastructure.persistence.adapter;

import com.yowyob.tiibntick.core.notify.application.port.out.INotificationPreferencePort;
import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import com.yowyob.tiibntick.core.notify.domain.model.NotificationPreference;
import com.yowyob.tiibntick.core.notify.infrastructure.persistence.entity.NotificationPreferenceEntity;
import com.yowyob.tiibntick.core.notify.infrastructure.persistence.repository.NotificationPreferenceR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter implementing INotificationPreferencePort via Spring Data R2DBC.
 *
 * @author MANFOUO Braun
 */
@Component
public class NotificationPreferenceRepositoryAdapter implements INotificationPreferencePort {

    private final NotificationPreferenceR2dbcRepository repository;

    public NotificationPreferenceRepositoryAdapter(NotificationPreferenceR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<NotificationPreference> findByUserId(String userId) {
        return repository.findById(userId).map(this::toDomain);
    }

    @Override
    public Mono<NotificationPreference> save(NotificationPreference preferences) {
        return repository.save(toEntity(preferences)).map(this::toDomain);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private NotificationPreferenceEntity toEntity(NotificationPreference p) {
        String csv = p.getActiveChannels().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
        return NotificationPreferenceEntity.builder()
                .userId(p.getUserId())
                .activeChannelsCsv(csv)
                .preferredLanguage(p.getPreferredLanguage())
                .notificationsEnabled(p.areNotificationsEnabled())
                .build();
    }

    private NotificationPreference toDomain(NotificationPreferenceEntity entity) {
        Set<NotificationChannel> channels;
        if (entity.getActiveChannelsCsv() == null || entity.getActiveChannelsCsv().isBlank()) {
            channels = EnumSet.allOf(NotificationChannel.class);
        } else {
            channels = Arrays.stream(entity.getActiveChannelsCsv().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(NotificationChannel::valueOf)
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(NotificationChannel.class)));
        }
        return new NotificationPreference(
                entity.getUserId(),
                channels,
                entity.getPreferredLanguage(),
                Boolean.TRUE.equals(entity.getNotificationsEnabled()));
    }
}
