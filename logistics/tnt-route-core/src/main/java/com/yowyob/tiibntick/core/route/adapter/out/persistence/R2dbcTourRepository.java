package com.yowyob.tiibntick.core.route.adapter.out.persistence;

import com.yowyob.tiibntick.core.route.adapter.out.persistence.entity.TourEntity;
import com.yowyob.tiibntick.core.route.application.port.out.ITourRepository;
import com.yowyob.tiibntick.core.route.domain.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.*;

@Repository
public class R2dbcTourRepository implements ITourRepository {

    private final R2dbcEntityTemplate template;
    private final ObjectMapper objectMapper;

    public R2dbcTourRepository(R2dbcEntityTemplate template,
            @Qualifier("routeObjectMapper") ObjectMapper objectMapper) {
        this.template = template;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Tour> save(Tour tour) {
        TourEntity e = toEntity(tour);
        return template.exists(Query.query(Criteria.where("id").is(e.getId())), TourEntity.class)
                .flatMap(exists -> exists ? template.update(e).thenReturn(e) : template.insert(e))
                .map(this::toDomain);
    }

    @Override
    public Mono<Tour> findById(UUID id, UUID tenantId) {
        Criteria criteria = Criteria.where("id").is(id);
        if (tenantId != null) {
            criteria = criteria.and("tenant_id").is(tenantId);
        }
        return template.selectOne(Query.query(criteria), TourEntity.class)
                .map(this::toDomain);
    }

    @Override
    public Flux<Tour> findByDeliverer(String delivererId, UUID tenantId, LocalDate date) {
        return template.select(Query.query(
                Criteria.where("deliverer_id").is(delivererId)
                        .and("tenant_id").is(tenantId)
                        .and("planning_date").is(date)), TourEntity.class)
                .map(this::toDomain);
    }

    @Override
    public Flux<Tour> findByAgencyAndDate(UUID tenantId, UUID agencyId, LocalDate date) {
        return template.select(Query.query(
                Criteria.where("tenant_id").is(tenantId)
                        .and("planning_date").is(date)), TourEntity.class)
                .map(this::toDomain);
    }

    private TourEntity toEntity(Tour t) {
        TourEntity e = new TourEntity();
        e.setId(t.id());
        e.setTenantId(t.tenantId());
        e.setDelivererId(t.delivererId());
        e.setTotalCost(t.totalCost());
        e.setTotalDistanceKm(t.totalDistanceKm());
        e.setPlanningDate(t.planningDate());
        e.setStatus(t.status().name());
        e.setCreatedAt(t.createdAt());
        e.setUpdatedAt(t.updatedAt());
        try { e.setStopsJson(objectMapper.writeValueAsString(t.stops())); }
        catch (Exception ex) { e.setStopsJson("[]"); }
        return e;
    }

    private Tour toDomain(TourEntity e) {
        List<TourStop> stops;
        try { stops = objectMapper.readValue(e.getStopsJson(), new TypeReference<>() {}); }
        catch (Exception ex) { stops = List.of(); }
        return Tour.rehydrate(e.getId(), e.getTenantId(), e.getDelivererId(), stops,
                e.getTotalCost(), e.getTotalDistanceKm(), e.getPlanningDate(),
                TourStatus.valueOf(e.getStatus()), e.getCreatedAt(), e.getUpdatedAt());
    }
}
