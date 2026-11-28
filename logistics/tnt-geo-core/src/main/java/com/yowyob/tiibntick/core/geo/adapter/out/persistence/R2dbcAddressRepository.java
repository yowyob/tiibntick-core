package com.yowyob.tiibntick.core.geo.adapter.out.persistence;

import com.yowyob.tiibntick.core.geo.application.port.out.IAddressRepository;
import com.yowyob.tiibntick.core.geo.domain.model.Address;
import com.yowyob.tiibntick.core.geo.domain.model.AddressLink;
import io.r2dbc.spi.Row;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * R2DBC implementation of {@link IAddressRepository} over {@code tnt_geography.tnt_addresses} and
 * {@code tnt_geography.tnt_address_links}. Plain SQL: the table has a caller-chosen primary key and an upsert is
 * the natural write.
 */
@Repository
public class R2dbcAddressRepository implements IAddressRepository {

    private static final String COLUMNS = "id, tenant_id, street, landmark, district, city, region, country, "
            + "postal_code, latitude, longitude, created_at, updated_at";

    /** The tenant sees its own addresses and the tenant-less ones (data migrated before tenant scoping). */
    private static final String TENANT_VISIBLE = "(tenant_id IS NULL OR tenant_id = :tenantId)";

    private final DatabaseClient db;

    public R2dbcAddressRepository(DatabaseClient databaseClient) {
        this.db = databaseClient;
    }

    @Override
    public Mono<Address> save(Address a) {
        String sql = "INSERT INTO tnt_geography.tnt_addresses (" + COLUMNS + ") VALUES ("
                + ":id, :tenantId, :street, :landmark, :district, :city, :region, :country, :postalCode, "
                + ":latitude, :longitude, :createdAt, :updatedAt) "
                + "ON CONFLICT (id) DO UPDATE SET street = EXCLUDED.street, landmark = EXCLUDED.landmark, "
                + "district = EXCLUDED.district, city = EXCLUDED.city, region = EXCLUDED.region, "
                + "country = EXCLUDED.country, postal_code = EXCLUDED.postal_code, latitude = EXCLUDED.latitude, "
                + "longitude = EXCLUDED.longitude, updated_at = EXCLUDED.updated_at";
        DatabaseClient.GenericExecuteSpec spec = db.sql(sql).bind("id", a.id());
        spec = bindNullable(spec, "tenantId", a.tenantId(), UUID.class);
        spec = bindNullable(spec, "street", a.street(), String.class);
        spec = bindNullable(spec, "landmark", a.landmark(), String.class);
        spec = bindNullable(spec, "district", a.district(), String.class);
        spec = spec.bind("city", a.city());
        spec = bindNullable(spec, "region", a.region(), String.class);
        spec = spec.bind("country", a.country());
        spec = bindNullable(spec, "postalCode", a.postalCode(), String.class);
        spec = bindNullable(spec, "latitude", a.latitude(), Double.class);
        spec = bindNullable(spec, "longitude", a.longitude(), Double.class);
        spec = spec.bind("createdAt", OffsetDateTime.ofInstant(a.createdAt(), java.time.ZoneOffset.UTC))
                .bind("updatedAt", OffsetDateTime.ofInstant(a.updatedAt(), java.time.ZoneOffset.UTC));
        return spec.fetch().rowsUpdated().thenReturn(a);
    }

    @Override
    public Mono<Address> findById(UUID tenantId, UUID id) {
        return tenantBound(db.sql("SELECT " + COLUMNS + " FROM tnt_geography.tnt_addresses WHERE id = :id AND "
                + TENANT_VISIBLE).bind("id", id), tenantId)
                .map((row, meta) -> toAddress(row)).one();
    }

    @Override
    public Flux<Address> findAll(UUID tenantId) {
        return tenantBound(db.sql("SELECT " + COLUMNS + " FROM tnt_geography.tnt_addresses WHERE "
                + TENANT_VISIBLE + " ORDER BY created_at, id"), tenantId)
                .map((row, meta) -> toAddress(row)).all();
    }

    @Override
    public Flux<Address> searchByText(UUID tenantId, String text) {
        String pattern = "%" + escapeLike(text) + "%";
        return tenantBound(db.sql("SELECT " + COLUMNS + " FROM tnt_geography.tnt_addresses WHERE "
                + TENANT_VISIBLE + " AND (landmark ILIKE :pattern ESCAPE '\\' OR street ILIKE :pattern ESCAPE '\\') "
                + "ORDER BY city, street, id").bind("pattern", pattern), tenantId)
                .map((row, meta) -> toAddress(row)).all();
    }

    /** The links go with the address (ON DELETE CASCADE). */
    @Override
    public Mono<Void> deleteById(UUID tenantId, UUID id) {
        return tenantBound(db.sql("DELETE FROM tnt_geography.tnt_addresses WHERE id = :id AND "
                + TENANT_VISIBLE).bind("id", id), tenantId).fetch().rowsUpdated().then();
    }

    @Override
    public Mono<AddressLink> saveLinkIfAbsent(AddressLink link) {
        DatabaseClient.GenericExecuteSpec insert = db.sql("INSERT INTO tnt_geography.tnt_address_links "
                        + "(id, owner_id, address_id, label, created_at) VALUES (:id, :ownerId, :addressId, :label, :createdAt) "
                        + "ON CONFLICT (owner_id, address_id, COALESCE(label, '')) DO NOTHING")
                .bind("id", link.id()).bind("ownerId", link.ownerId()).bind("addressId", link.addressId())
                .bind("createdAt", OffsetDateTime.ofInstant(link.createdAt(), java.time.ZoneOffset.UTC));
        insert = bindNullable(insert, "label", link.label(), String.class);

        DatabaseClient.GenericExecuteSpec select = db.sql("SELECT id, owner_id, address_id, label, created_at "
                        + "FROM tnt_geography.tnt_address_links WHERE owner_id = :ownerId AND address_id = :addressId "
                        + "AND COALESCE(label, '') = COALESCE(:label, '')")
                .bind("ownerId", link.ownerId()).bind("addressId", link.addressId());
        select = bindNullable(select, "label", link.label(), String.class);
        return insert.fetch().rowsUpdated().then(select.map((row, meta) -> toLink(row)).one());
    }

    @Override
    public Flux<AddressLink> findLinksByOwner(UUID ownerId) {
        return db.sql("SELECT id, owner_id, address_id, label, created_at FROM tnt_geography.tnt_address_links "
                        + "WHERE owner_id = :ownerId ORDER BY created_at, id")
                .bind("ownerId", ownerId).map((row, meta) -> toLink(row)).all();
    }

    @Override
    public Flux<AddressLink> findLinksByAddress(UUID addressId) {
        return db.sql("SELECT id, owner_id, address_id, label, created_at FROM tnt_geography.tnt_address_links "
                        + "WHERE address_id = :addressId ORDER BY created_at, id")
                .bind("addressId", addressId).map((row, meta) -> toLink(row)).all();
    }

    @Override
    public Mono<Void> deleteLinks(UUID ownerId, UUID addressId) {
        return db.sql("DELETE FROM tnt_geography.tnt_address_links WHERE owner_id = :ownerId AND address_id = :addressId")
                .bind("ownerId", ownerId).bind("addressId", addressId).fetch().rowsUpdated().then();
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static DatabaseClient.GenericExecuteSpec tenantBound(DatabaseClient.GenericExecuteSpec spec, UUID tenantId) {
        return bindNullable(spec, "tenantId", tenantId, UUID.class);
    }

    private static <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                      String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Address toAddress(Row row) {
        return Address.rehydrate(row.get("id", UUID.class), row.get("tenant_id", UUID.class),
                row.get("street", String.class), row.get("landmark", String.class), row.get("district", String.class),
                row.get("city", String.class), row.get("region", String.class), row.get("country", String.class),
                row.get("postal_code", String.class), row.get("latitude", Double.class),
                row.get("longitude", Double.class), instant(row, "created_at"), instant(row, "updated_at"));
    }

    private static AddressLink toLink(Row row) {
        return new AddressLink(row.get("id", UUID.class), row.get("owner_id", UUID.class),
                row.get("address_id", UUID.class), row.get("label", String.class), instant(row, "created_at"));
    }

    private static Instant instant(Row row, String column) {
        OffsetDateTime t = row.get(column, OffsetDateTime.class);
        return t != null ? t.toInstant() : Instant.now();
    }
}
