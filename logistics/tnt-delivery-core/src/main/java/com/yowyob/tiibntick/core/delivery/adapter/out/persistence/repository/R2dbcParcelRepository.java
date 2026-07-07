package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.ParcelEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code ParcelEntity}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcParcelRepository extends ReactiveCrudRepository<ParcelEntity, UUID> {}
