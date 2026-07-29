package com.yowyob.tiibntick.core.gofreelancer.adapter.out.search;

import com.yowyob.tiibntick.core.gofreelancer.adapter.out.search.document.FreelancerDocument;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.geo.Distance;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

import java.util.UUID;

/**
 * Reactive Repository for accessing Freelancer documents in Elasticsearch.
 *
 * @author François-Charles ATANGA
 * @date 03/02/2026
 */
@Repository
public interface FreelancerSearchRepository extends ReactiveElasticsearchRepository<FreelancerDocument, UUID> {
    Flux<FreelancerDocument> findByIsAvailableTrueAndIsActiveTrueAndLocationNear(GeoPoint location,
            Distance distance);
}
