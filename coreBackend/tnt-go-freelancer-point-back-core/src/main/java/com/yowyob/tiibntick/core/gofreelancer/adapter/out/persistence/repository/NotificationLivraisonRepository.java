package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NotificationLivraison;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive R2DBC repository for NotificationLivraison entity.
 * Converted from blocking CrudRepository to ReactiveCrudRepository (§4 of the review report).
 *
 * @author Kengfack Lagrange
 */
@Repository
public interface NotificationLivraisonRepository extends ReactiveCrudRepository<NotificationLivraison, UUID> {
}
