package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence;

import com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity.ApiKeyRotationHistoryEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Spring Data R2DBC repository for {@code tnt_api_key_rotation_history}.
 *
 * @author MANFOUO Braun
 */
public interface ApiKeyRotationHistoryR2dbcRepository extends ReactiveCrudRepository<ApiKeyRotationHistoryEntity, String> {
}
