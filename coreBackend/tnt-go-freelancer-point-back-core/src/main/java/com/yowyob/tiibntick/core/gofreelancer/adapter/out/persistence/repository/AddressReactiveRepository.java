package com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.entity.AddressEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Repository R2DBC pour la persistance des adresses.
 */
public interface AddressReactiveRepository extends ReactiveCrudRepository<AddressEntity, UUID> {
    
    /**
     * Recherche textuelle simple (par repère ou rue).
     */
    Flux<AddressEntity> findByLandmarkContainingIgnoreCaseOrStreetContainingIgnoreCase(String landmark, String street);
}
