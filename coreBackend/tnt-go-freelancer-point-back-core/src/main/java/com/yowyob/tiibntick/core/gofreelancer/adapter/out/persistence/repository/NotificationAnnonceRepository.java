package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.NotificationAnnonce;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

/**
 * Reactive R2DBC repository for NotificationAnnonce entity.
 * Converted from blocking CrudRepository to ReactiveCrudRepository (§4 of the review report).
 *
 * @author Kengfack Lagrange
 */
@Repository
public interface NotificationAnnonceRepository extends ReactiveCrudRepository<NotificationAnnonce, UUID> {
}
