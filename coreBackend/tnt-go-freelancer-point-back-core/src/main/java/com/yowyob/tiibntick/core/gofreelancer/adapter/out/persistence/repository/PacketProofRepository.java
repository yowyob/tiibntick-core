package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.domain.model.PacketProof;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;
import java.util.UUID;

public interface PacketProofRepository extends ReactiveCrudRepository<PacketProof, UUID> {
    Mono<PacketProof> findByCorePacketId(UUID corePacketId);
}
