package com.yowyob.tiibntick.core.marketback.adapter.out.messaging;

import com.yowyob.tiibntick.core.marketback.application.port.in.query.MarketSearchQuery;
import com.yowyob.tiibntick.core.marketback.application.port.out.IMarketSearchIndexPort;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListing;
import com.yowyob.tiibntick.core.marketback.domain.model.MarketListingId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ReactiveElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Elasticsearch adapter for market listing full-text search and indexing.
 * Ported from the standalone tiibntick-market-backend's
 * {@code SearchIndexClientAdapter}, unchanged in behavior.
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketSearchIndexAdapter implements IMarketSearchIndexPort {

    private final ReactiveElasticsearchOperations elasticsearchOperations;

    @Override
    public Mono<Void> indexListing(MarketListing listing) {
        log.debug("Indexing listing id={}", listing.getId().value());
        MarketListingDocument doc = MarketListingDocument.from(listing);
        return elasticsearchOperations.save(doc).then();
    }

    @Override
    public Mono<Void> removeListing(MarketListingId listingId) {
        return elasticsearchOperations.delete(listingId.value().toString(), MarketListingDocument.class).then();
    }

    @Override
    public Mono<Void> updateRating(MarketListingId listingId, double newAvgRating, int totalReviews) {
        return elasticsearchOperations.get(listingId.value().toString(), MarketListingDocument.class)
                .flatMap(doc -> {
                    doc.setAverageRating(newAvgRating);
                    return elasticsearchOperations.save(doc);
                })
                .then();
    }

    @Override
    public Flux<UUID> searchListings(MarketSearchQuery query) {
        Criteria criteria = new Criteria();
        if (query.keyword() != null && !query.keyword().isBlank()) {
            criteria = criteria.and(new Criteria("displayName").matches(query.keyword()))
                    .or(new Criteria("description").matches(query.keyword()))
                    .or(new Criteria("tagline").matches(query.keyword()));
        }
        if (query.city() != null) {
            criteria = criteria.and(new Criteria("coverageCities").contains(query.city()));
        }
        if (query.serviceType() != null) {
            criteria = criteria.and(new Criteria("serviceTypes").is(query.serviceType().name()));
        }
        if (query.providerType() != null) {
            criteria = criteria.and(new Criteria("providerType").is(query.providerType().name()));
        }
        CriteriaQuery cq = new CriteriaQuery(criteria);
        return elasticsearchOperations.search(cq, MarketListingDocument.class)
                .map(SearchHit::getContent)
                .map(doc -> UUID.fromString(doc.getId()));
    }

    /** Internal Elasticsearch document model for market listings. */
    @org.springframework.data.elasticsearch.annotations.Document(indexName = "market_listings")
    public static class MarketListingDocument {
        @org.springframework.data.annotation.Id
        private String id;
        private String tenantId;
        private String displayName;
        private String tagline;
        private String description;
        private String providerType;
        private List<String> coverageCities;
        private List<String> serviceTypes;
        private double averageRating;

        public static MarketListingDocument from(MarketListing l) {
            MarketListingDocument d = new MarketListingDocument();
            d.id = l.getId().value().toString();
            d.tenantId = l.getTenantId();
            if (l.getVitrine() != null) {
                d.displayName = l.getVitrine().displayName();
                d.tagline = l.getVitrine().tagline();
                d.description = l.getVitrine().description();
            }
            d.providerType = l.getProviderType() != null ? l.getProviderType().name() : null;
            d.averageRating = l.getAverageRating();
            if (l.getCoverageZone() != null) d.coverageCities = l.getCoverageZone().cities();
            return d;
        }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTenantId() { return tenantId; }
        public void setTenantId(String v) { tenantId = v; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String v) { displayName = v; }
        public String getTagline() { return tagline; }
        public void setTagline(String v) { tagline = v; }
        public String getDescription() { return description; }
        public void setDescription(String v) { description = v; }
        public String getProviderType() { return providerType; }
        public void setProviderType(String v) { providerType = v; }
        public List<String> getCoverageCities() { return coverageCities; }
        public void setCoverageCities(List<String> v) { coverageCities = v; }
        public List<String> getServiceTypes() { return serviceTypes; }
        public void setServiceTypes(List<String> v) { serviceTypes = v; }
        public double getAverageRating() { return averageRating; }
        public void setAverageRating(double v) { averageRating = v; }
    }
}
