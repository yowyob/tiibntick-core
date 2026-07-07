package com.yowyob.tiibntick.core.incident.port.outbound;
import com.yowyob.tiibntick.core.incident.domain.model.IncidentBlockchainRecord;
import com.yowyob.tiibntick.core.incident.domain.model.ParcelIncidentLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.UUID;
/**
 * Outbound port: persistence of blockchain records and parcel-incident links.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

public interface IIncidentBlockchainRepository {
    Mono<IncidentBlockchainRecord> save(IncidentBlockchainRecord record);
    Flux<IncidentBlockchainRecord> findByChainIdOrderByBlockIndex(String chainId);
    Mono<IncidentBlockchainRecord> findLatestByChainId(String chainId);
    Mono<ParcelIncidentLink> saveLink(ParcelIncidentLink link);
    Flux<ParcelIncidentLink> findLinksByIncidentId(UUID incidentId);
    Mono<ParcelIncidentLink> findLinkByParcelAndIncident(UUID parcelId, UUID incidentId);
}
