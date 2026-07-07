package com.yowyob.tiibntick.core.notify.infrastructure.persistence.repository;

import com.yowyob.tiibntick.core.notify.infrastructure.persistence.entity.NotificationPreferenceEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Reactive R2DBC repository for user notification preferences.
 *
 * @author MANFOUO Braun
 */
public interface NotificationPreferenceR2dbcRepository
                extends ReactiveCrudRepository<NotificationPreferenceEntity, String> {
}
