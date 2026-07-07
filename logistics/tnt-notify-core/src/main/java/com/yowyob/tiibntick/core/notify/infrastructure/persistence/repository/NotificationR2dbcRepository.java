package com.yowyob.tiibntick.core.notify.infrastructure.persistence.repository;

import com.yowyob.tiibntick.core.notify.infrastructure.persistence.entity.NotificationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

/**
 * Reactive R2DBC repository for notification entities.
 *
 * @author Dilane PAFE
 * @author MANFOUO Braun
 */
public interface NotificationR2dbcRepository extends ReactiveCrudRepository<NotificationEntity, String> {

    Flux<NotificationEntity> findByRecipientId(String recipientId);

    Flux<NotificationEntity> findByStatus(String status);

    @Query("SELECT * FROM tnt_notifications WHERE destinataire_id = :recipientId AND statut = 'PENDING' " +
            "ORDER BY date_creation DESC")
    Flux<NotificationEntity> findPendingByRecipient(String recipientId);
}
