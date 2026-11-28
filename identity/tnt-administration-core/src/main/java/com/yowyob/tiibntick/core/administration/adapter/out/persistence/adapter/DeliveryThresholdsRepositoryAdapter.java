package com.yowyob.tiibntick.core.administration.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.administration.application.port.out.DeliveryThresholdsRepository;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThreshold;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.DeliveryThresholdValues;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.OrganizationDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.PlatformDeliveryThresholds;
import com.yowyob.tiibntick.core.administration.domain.model.thresholds.RelayStorageBounds;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * R2DBC adapter of {@link DeliveryThresholdsRepository} on {@code administration.tnt_delivery_thresholds} (single
 * row {@code id = 'PLATFORM'}) and {@code administration.tnt_organization_delivery_thresholds} (changesets
 * {@code evt-l1-001/002}). Columns are driven by {@link DeliveryThreshold#column()}; optimistic locking is a
 * {@code WHERE version = :expected} guard with {@code RETURNING *}.
 */
@Component
public class DeliveryThresholdsRepositoryAdapter implements DeliveryThresholdsRepository {

    static final String PLATFORM_TABLE = "administration.tnt_delivery_thresholds";
    static final String ORGANIZATION_TABLE = "administration.tnt_organization_delivery_thresholds";
    private static final String RELAY_MIN = "independent_relay_storage_min_days";
    private static final String RELAY_MAX = "independent_relay_storage_max_days";

    private static final String THRESHOLD_COLUMNS = Arrays.stream(DeliveryThreshold.values())
            .map(DeliveryThreshold::column).collect(Collectors.joining(", "));
    private static final String THRESHOLD_PARAMS = Arrays.stream(DeliveryThreshold.values())
            .map(t -> ":" + t.column()).collect(Collectors.joining(", "));
    private static final String THRESHOLD_ASSIGNMENTS = Arrays.stream(DeliveryThreshold.values())
            .map(t -> t.column() + " = :" + t.column()).collect(Collectors.joining(", "));

    private final DatabaseClient db;

    public DeliveryThresholdsRepositoryAdapter(DatabaseClient db) {
        this.db = db;
    }

    // ── platform ──────────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<PlatformDeliveryThresholds> findPlatform() {
        return db.sql("SELECT * FROM " + PLATFORM_TABLE + " WHERE id = 'PLATFORM'")
                .map(DeliveryThresholdsRepositoryAdapter::toPlatform).one();
    }

    @Override
    public Mono<PlatformDeliveryThresholds> updatePlatform(PlatformDeliveryThresholds next, long expectedVersion) {
        GenericExecuteSpec spec = db.sql("UPDATE " + PLATFORM_TABLE + " SET " + THRESHOLD_ASSIGNMENTS + ", "
                + RELAY_MIN + " = :" + RELAY_MIN + ", " + RELAY_MAX + " = :" + RELAY_MAX
                + ", updated_by = :updated_by, updated_at = :updated_at, version = version + 1"
                + " WHERE id = 'PLATFORM' AND version = :expected RETURNING *");
        spec = bindThresholds(spec, next.values());
        spec = bindNullable(spec, RELAY_MIN, next.independentRelayStorage().minDays(), Integer.class);
        spec = bindNullable(spec, RELAY_MAX, next.independentRelayStorage().maxDays(), Integer.class);
        spec = bindAudit(spec, next.updatedBy(), next.updatedAt()).bind("expected", expectedVersion);
        return spec.map(DeliveryThresholdsRepositoryAdapter::toPlatform).one();
    }

    // ── organizations ─────────────────────────────────────────────────────────────────────────

    @Override
    public Mono<OrganizationDeliveryThresholds> findOrganization(UUID organizationId) {
        return db.sql("SELECT * FROM " + ORGANIZATION_TABLE + " WHERE organization_id = :organizationId")
                .bind("organizationId", organizationId)
                .map(DeliveryThresholdsRepositoryAdapter::toOrganization).one();
    }

    @Override
    public Flux<OrganizationDeliveryThresholds> findAllOrganizations() {
        return db.sql("SELECT * FROM " + ORGANIZATION_TABLE + " ORDER BY updated_at DESC")
                .map(DeliveryThresholdsRepositoryAdapter::toOrganization).all();
    }

    @Override
    public Mono<OrganizationDeliveryThresholds> insertOrganization(OrganizationDeliveryThresholds override) {
        GenericExecuteSpec spec = db.sql("INSERT INTO " + ORGANIZATION_TABLE + " (organization_id, "
                + THRESHOLD_COLUMNS + ", updated_by, updated_at, version) VALUES (:organizationId, "
                + THRESHOLD_PARAMS + ", :updated_by, :updated_at, 0)"
                + " ON CONFLICT (organization_id) DO NOTHING RETURNING *")
                .bind("organizationId", override.organizationId());
        spec = bindAudit(bindThresholds(spec, override.values()), override.updatedBy(), override.updatedAt());
        return spec.map(DeliveryThresholdsRepositoryAdapter::toOrganization).one();
    }

    @Override
    public Mono<OrganizationDeliveryThresholds> updateOrganization(OrganizationDeliveryThresholds override,
                                                                   long expectedVersion) {
        GenericExecuteSpec spec = db.sql("UPDATE " + ORGANIZATION_TABLE + " SET " + THRESHOLD_ASSIGNMENTS
                + ", updated_by = :updated_by, updated_at = :updated_at, version = version + 1"
                + " WHERE organization_id = :organizationId AND version = :expected RETURNING *")
                .bind("organizationId", override.organizationId())
                .bind("expected", expectedVersion);
        spec = bindAudit(bindThresholds(spec, override.values()), override.updatedBy(), override.updatedAt());
        return spec.map(DeliveryThresholdsRepositoryAdapter::toOrganization).one();
    }

    @Override
    public Mono<Boolean> deleteOrganization(UUID organizationId) {
        return db.sql("DELETE FROM " + ORGANIZATION_TABLE + " WHERE organization_id = :organizationId")
                .bind("organizationId", organizationId)
                .fetch().rowsUpdated()
                .map(rows -> rows > 0);
    }

    // ── mapping ───────────────────────────────────────────────────────────────────────────────

    private static GenericExecuteSpec bindThresholds(GenericExecuteSpec spec, DeliveryThresholdValues values) {
        GenericExecuteSpec bound = spec;
        for (DeliveryThreshold t : DeliveryThreshold.values()) {
            bound = bindNullable(bound, t.column(), values.get(t), Integer.class);
        }
        return bound;
    }

    private static GenericExecuteSpec bindAudit(GenericExecuteSpec spec, UUID updatedBy, Instant updatedAt) {
        return bindNullable(spec, "updated_by", updatedBy, UUID.class)
                .bind("updated_at", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC));
    }

    private static <T> GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static DeliveryThresholdValues readValues(Readable row) {
        Map<DeliveryThreshold, Integer> map = new EnumMap<>(DeliveryThreshold.class);
        for (DeliveryThreshold t : DeliveryThreshold.values()) {
            Integer value = row.get(t.column(), Integer.class);
            if (value != null) {
                map.put(t, value);
            }
        }
        return DeliveryThresholdValues.of(map);
    }

    private static Instant instant(Readable row, String column) {
        OffsetDateTime value = row.get(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static long version(Readable row) {
        Long value = row.get("version", Long.class);
        return value == null ? 0L : value;
    }

    static PlatformDeliveryThresholds toPlatform(Readable row) {
        return new PlatformDeliveryThresholds(readValues(row),
                new RelayStorageBounds(row.get(RELAY_MIN, Integer.class), row.get(RELAY_MAX, Integer.class)),
                row.get("updated_by", UUID.class), instant(row, "updated_at"), version(row));
    }

    static OrganizationDeliveryThresholds toOrganization(Readable row) {
        return new OrganizationDeliveryThresholds(row.get("organization_id", UUID.class), readValues(row),
                row.get("updated_by", UUID.class), instant(row, "updated_at"), version(row));
    }
}
