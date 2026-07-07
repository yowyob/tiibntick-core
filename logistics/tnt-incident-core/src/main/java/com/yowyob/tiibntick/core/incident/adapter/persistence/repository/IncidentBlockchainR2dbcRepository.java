package com.yowyob.tiibntick.core.incident.adapter.persistence.repository;
import com.yowyob.tiibntick.core.incident.adapter.persistence.entity.IncidentBlockchainRecordEntity;
import com.yowyob.tiibntick.core.incident.adapter.persistence.entity.ParcelIncidentLinkEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Spring Data R2DBC repository for IncidentBlockchainRecord entities.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IncidentBlockchainR2dbcRepository extends ReactiveCrudRepository<IncidentBlockchainRecordEntity, UUID> {
    Flux<IncidentBlockchainRecordEntity> findByChainIdOrderByBlockIndexAsc(String chainId);
    @Query("SELECT * FROM tnt_incident_blockchain_records WHERE chain_id = :chainId ORDER BY block_index DESC LIMIT 1")
    Mono<IncidentBlockchainRecordEntity> findLatestByChainId(String chainId);
}
