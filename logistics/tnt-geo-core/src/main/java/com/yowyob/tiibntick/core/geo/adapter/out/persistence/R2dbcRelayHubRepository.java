package com.yowyob.tiibntick.core.geo.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.adapter.out.persistence.entity.RelayHubEntity;
import com.yowyob.tiibntick.core.geo.application.port.out.IRelayHubRepository;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.HubStatus;
import com.yowyob.tiibntick.core.geo.domain.model.RelayHub;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.data.relational.core.query.Update;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

/**
 * R2DBC implementation of {@link IRelayHubRepository}.
 * Spatial queries join road_nodes to get geographic coordinates of each hub's node.
 *
 * Author: MANFOUO Braun
 */
@Repository
public class R2dbcRelayHubRepository implements IRelayHubRepository {

    private final R2dbcEntityTemplate template;
    private final DatabaseClient databaseClient;

    public R2dbcRelayHubRepository(R2dbcEntityTemplate template, DatabaseClient databaseClient) {
        this.template = template;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<RelayHub> save(RelayHub hub) {
        RelayHubEntity entity = RelayHubEntity.fromDomain(hub);
        return template.exists(
                        Query.query(Criteria.where("id").is(entity.getId())),
                        RelayHubEntity.class)
                .flatMap(exists -> exists
                        ? template.update(entity).thenReturn(entity)
                        : template.insert(entity))
                .map(RelayHubEntity::toDomain);
    }

    @Override
    public Mono<RelayHub> findById(UUID id, UUID tenantId) {
        Criteria criteria = Criteria.where("id").is(id);
        if (tenantId != null) criteria = criteria.and("tenant_id").is(tenantId);
        return template.selectOne(Query.query(criteria), RelayHubEntity.class)
                .map(RelayHubEntity::toDomain);
    }

    @Override
    public Flux<RelayHub> findByBranch(UUID branchId, UUID tenantId) {
        Criteria criteria = Criteria.where("branch_id").is(branchId);
        if (tenantId != null) criteria = criteria.and("tenant_id").is(tenantId);
        return template.select(Query.query(criteria), RelayHubEntity.class)
                .map(RelayHubEntity::toDomain);
    }

    @Override
    public Flux<RelayHub> findAllActive(UUID tenantId) {
        return template.select(
                        Query.query(Criteria.where("tenant_id").is(tenantId)
                                .and("status").is(HubStatus.ACTIVE.name())),
                        RelayHubEntity.class)
                .map(RelayHubEntity::toDomain);
    }

    /**
     * Spatial query joining relay_hubs with road_nodes to get hub coordinates.
     * Filters ACTIVE hubs within the given radius, ordered by proximity.
     */
    @Override
    public Flux<RelayHub> findAvailableWithinRadius(UUID tenantId, GeoPoint center, double radiusKm) {
        double radiusMetres = radiusKm * 1000.0;
        String sql = String.format(java.util.Locale.ROOT, """
                SELECT h.id, h.tenant_id, h.branch_id, h.node_id,
                       h.capacity_slots, h.current_occupancy, h.operator_actor_id,
                       h.status, h.created_at, h.updated_at
                FROM tnt_geography.relay_hubs h
                INNER JOIN tnt_geography.road_nodes n ON n.id = h.node_id
                WHERE h.tenant_id = :tenantId
                  AND h.status = 'ACTIVE'
                  AND h.current_occupancy < h.capacity_slots
                  AND ST_DWithin(
                        ST_MakePoint(n.longitude, n.latitude)::geography,
                        ST_MakePoint(%.8f, %.8f)::geography,
                        %.2f
                      )
                ORDER BY ST_Distance(
                    ST_MakePoint(n.longitude, n.latitude)::geography,
                    ST_MakePoint(%.8f, %.8f)::geography
                )
                """, center.longitude(), center.latitude(), radiusMetres,
                     center.longitude(), center.latitude());
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .map(row -> {
                    RelayHubEntity e = new RelayHubEntity();
                    e.setId(row.get("id", UUID.class));
                    e.setTenantId(row.get("tenant_id", UUID.class));
                    e.setBranchId(row.get("branch_id", UUID.class));
                    e.setNodeId(row.get("node_id", String.class));
                    e.setCapacitySlots(row.get("capacity_slots", Integer.class));
                    e.setCurrentOccupancy(row.get("current_occupancy", Integer.class));
                    e.setOperatorActorId(row.get("operator_actor_id", String.class));
                    e.setStatus(row.get("status", String.class));
                    e.setCreatedAt(row.get("created_at", Instant.class));
                    e.setUpdatedAt(row.get("updated_at", Instant.class));
                    return e;
                })
                .all()
                .map(RelayHubEntity::toDomain);
    }

    @Override
    public Mono<RelayHub> updateOccupancy(UUID id, UUID tenantId, int newOccupancy) {
        String newStatus = newOccupancy > 0 ? HubStatus.FULL.name() : HubStatus.ACTIVE.name();
        return template.update(
                        Query.query(Criteria.where("id").is(id).and("tenant_id").is(tenantId)),
                        Update.update("current_occupancy", newOccupancy)
                                .set("status", newStatus)
                                .set("updated_at", Instant.now()),
                        RelayHubEntity.class)
                .flatMap(count -> count > 0 ? findById(id, tenantId) : Mono.empty());
    }
}
