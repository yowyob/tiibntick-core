package com.yowyob.tiibntick.core.geo.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.application.port.out.IServiceZoneRepository;
import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import com.yowyob.tiibntick.core.geo.domain.model.ServiceZonePolygon;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * R2DBC implementation of {@link IServiceZoneRepository}.
 * Polygon vertices are stored as JSONB in PostgreSQL for simplicity with R2DBC.
 * PostGIS POLYGON type would require a custom codec; JSONB deserialization is
 * portable.
 *
 * Author: MANFOUO Braun
 */
@Repository
public class R2dbcServiceZoneRepository implements IServiceZoneRepository {

    //private static final String SCHEMA = "tnt_geography";
    private static final TypeReference<List<double[]>> VERTEX_TYPE = new TypeReference<>() {
    };

    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public R2dbcServiceZoneRepository(DatabaseClient databaseClient,
            @Qualifier("geoObjectMapper") ObjectMapper objectMapper) {
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<ServiceZonePolygon> save(ServiceZonePolygon zone) {
        String verticesJson = serializeVertices(zone.vertices());
        String sql = """
                INSERT INTO tnt_geography.service_zones
                  (id, tenant_id, agency_id, name, vertices_json, is_active,
                   freelancer_org_id, owner_type, created_at, updated_at)
                VALUES (:id, :tenantId, :agencyId, :name, :vertices::jsonb, :active,
                        :freelancerOrgId, :ownerType, :createdAt, :updatedAt)
                ON CONFLICT (id) DO UPDATE
                  SET name = EXCLUDED.name,
                      vertices_json = EXCLUDED.vertices_json,
                      is_active = EXCLUDED.is_active,
                      freelancer_org_id = EXCLUDED.freelancer_org_id,
                      owner_type = EXCLUDED.owner_type,
                      updated_at = EXCLUDED.updated_at
                """;
        var spec = databaseClient.sql(sql)
                .bind("id", zone.id())
                .bind("tenantId", zone.tenantId())
                .bind("name", zone.name())
                .bind("vertices", verticesJson)
                .bind("active", zone.isActive())
                .bind("ownerType", zone.ownerType() != null ? zone.ownerType() : "AGENCY")
                .bind("createdAt", Instant.now())
                .bind("updatedAt", Instant.now());
        if (zone.agencyId() != null) {
            spec = spec.bind("agencyId", zone.agencyId());
        } else {
            spec = spec.bindNull("agencyId", UUID.class);
        }
        if (zone.freelancerOrgId() != null) {
            spec = spec.bind("freelancerOrgId", zone.freelancerOrgId());
        } else {
            spec = spec.bindNull("freelancerOrgId", String.class);
        }
        return spec.fetch().rowsUpdated().thenReturn(zone);
    }

    @Override
    public Mono<ServiceZonePolygon> findById(UUID id, UUID tenantId) {
        String sql = """
                SELECT id, tenant_id, agency_id, name, vertices_json::text AS vertices_json, is_active
                FROM tnt_geography.service_zones
                WHERE id = :id
                """;
        if (tenantId != null) {
            return databaseClient.sql(sql + " AND tenant_id = :tenantId")
                    .bind("id", id)
                    .bind("tenantId", tenantId)
                    .map((io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) -> mapToZone(row))
                    .one();
        }
        return databaseClient.sql(sql)
                .bind("id", id)
                .map((io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) -> mapToZone(row))
                .one();
    }

    @Override
    public Flux<ServiceZonePolygon> findByAgency(UUID agencyId, UUID tenantId) {
        String sql = """
                SELECT id, tenant_id, agency_id, name, vertices_json::text AS vertices_json, is_active
                FROM tnt_geography.service_zones
                WHERE agency_id = :agencyId AND tenant_id = :tenantId
                """;
        return databaseClient.sql(sql)
                .bind("agencyId", agencyId)
                .bind("tenantId", tenantId)
                .map((io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) -> mapToZone(row))
                .all();
    }

    @Override
    public Flux<ServiceZonePolygon> findAllActiveByTenant(UUID tenantId) {
        String sql = """
                SELECT id, tenant_id, agency_id, name, vertices_json::text AS vertices_json, is_active
                FROM tnt_geography.service_zones
                WHERE tenant_id = :tenantId AND is_active = true
                """;
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .map((io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) -> mapToZone(row))
                .all();
    }

    private ServiceZonePolygon mapToZone(io.r2dbc.spi.Row row) {
        UUID id = row.get("id", UUID.class);
        UUID tenantId = row.get("tenant_id", UUID.class);
        UUID agencyId = row.get("agency_id", UUID.class);
        String name = row.get("name", String.class);
        String verticesJson = row.get("vertices_json", String.class);
        boolean active = Boolean.TRUE.equals(row.get("is_active", Boolean.class));
        List<GeoPoint> vertices = deserializeVertices(verticesJson);
        return ServiceZonePolygon.rehydrate(id, tenantId, agencyId, name, vertices, active);
    }

    private String serializeVertices(List<GeoPoint> vertices) {
        List<double[]> coords = vertices.stream()
                .map(p -> new double[] { p.latitude(), p.longitude() })
                .toList();
        try {
            return objectMapper.writeValueAsString(coords);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize polygon vertices", e);
        }
    }

    @Override
    public Flux<ServiceZonePolygon> findByFreelancerOrg(String freelancerOrgId, UUID tenantId) {
        String sql = """
                SELECT id, tenant_id, agency_id, name, vertices_json::text AS vertices_json,
                       is_active, freelancer_org_id, owner_type
                FROM tnt_geography.service_zones
                WHERE freelancer_org_id = :orgId AND tenant_id = :tenantId AND is_active = true
                """;
        return databaseClient.sql(sql)
                .bind("orgId", freelancerOrgId)
                .bind("tenantId", tenantId)
                .map((io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) -> mapRow(row))
                .all();
    }

    @Override
    public Flux<ServiceZonePolygon> findAllActiveFreelancerOrgZonesByTenant(UUID tenantId) {
        String sql = """
                SELECT id, tenant_id, agency_id, name, vertices_json::text AS vertices_json,
                       is_active, freelancer_org_id, owner_type
                FROM tnt_geography.service_zones
                WHERE tenant_id = :tenantId AND owner_type = 'FREELANCER_ORG' AND is_active = true
                """;
        return databaseClient.sql(sql)
                .bind("tenantId", tenantId)
                .map((io.r2dbc.spi.Row row, io.r2dbc.spi.RowMetadata meta) -> mapRow(row))
                .all();
    }

    private ServiceZonePolygon mapRow(io.r2dbc.spi.Row row) {
        // Attempt to read fields, fallback gracefully
        String freelancerOrgId = null;
        String ownerType = "AGENCY";
        try {
            freelancerOrgId = row.get("freelancer_org_id", String.class);
            ownerType = row.get("owner_type", String.class);
        } catch (Exception ignored) {
        }
        return ServiceZonePolygon.rehydrateFull(
                row.get("id", java.util.UUID.class),
                row.get("tenant_id", java.util.UUID.class),
                row.get("agency_id", java.util.UUID.class),
                row.get("name", String.class),
                deserializeVertices(row.get("vertices_json", String.class)),
                Boolean.TRUE.equals(row.get("is_active", Boolean.class)),
                freelancerOrgId, ownerType);
    }

    //private ServiceZonePolygon mapRow(java.util.Map<String, Object> rowMap) {
    //    throw new UnsupportedOperationException("Use mapRow(Row) instead");
    //}

    private List<GeoPoint> deserializeVertices(String json) {
        if (json == null || json.isBlank())
            return List.of();
        try {
            List<double[]> coords = objectMapper.readValue(json, VERTEX_TYPE);
            return coords.stream()
                    .map(c -> GeoPoint.of(c[0], c[1]))
                    .toList();
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize polygon vertices", e);
        }
    }
}
