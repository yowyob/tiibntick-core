package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ICourierLegRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CourierLeg;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** {@link ICourierLegRepository} over {@link DatabaseClient} ({@code billing.wallet_courier_legs}); UTC timestamps. */
@Component
public class CourierLegRepositoryAdapter implements ICourierLegRepository {

    private final DatabaseClient db;

    public CourierLegRepositoryAdapter(DatabaseClient db) {
        this.db = db;
    }

    @Override
    public Mono<Void> insertIfAbsent(CourierLeg l) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("INSERT INTO billing.wallet_courier_legs (id, tenant_id,"
                        + " delivery_id, event_id, aggregate_version, replaced_courier_id, replacement_courier_id,"
                        + " replaced_leg_km, remaining_km, created_at) VALUES (:id, :tenant, :delivery, :event,"
                        + " :version, :replaced, :replacement, :legKm, :remainingKm, :created)"
                        + " ON CONFLICT (event_id) DO NOTHING")
                .bind("id", l.id() != null ? l.id() : UUID.randomUUID())
                .bind("tenant", l.tenantId()).bind("delivery", l.deliveryId()).bind("event", l.eventId())
                .bind("version", l.aggregateVersion()).bind("replaced", l.replacedCourierId())
                .bind("replacement", l.replacementCourierId())
                .bind("created", LocalDateTime.ofInstant(l.createdAt() != null ? l.createdAt() : Instant.now(),
                        ZoneOffset.UTC));
        spec = l.replacedLegKm() == null ? spec.bindNull("legKm", BigDecimal.class)
                : spec.bind("legKm", l.replacedLegKm());
        spec = l.remainingKm() == null ? spec.bindNull("remainingKm", BigDecimal.class)
                : spec.bind("remainingKm", l.remainingKm());
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Flux<CourierLeg> byDelivery(UUID deliveryId) {
        return db.sql("SELECT * FROM billing.wallet_courier_legs WHERE delivery_id = :d ORDER BY aggregate_version")
                .bind("d", deliveryId).map(CourierLegRepositoryAdapter::leg).all();
    }

    private static CourierLeg leg(Readable r) {
        LocalDateTime created = r.get("created_at", LocalDateTime.class);
        Long version = r.get("aggregate_version", Long.class);
        return new CourierLeg(r.get("id", UUID.class), r.get("tenant_id", UUID.class), r.get("delivery_id", UUID.class),
                r.get("event_id", UUID.class), version != null ? version : 0L, r.get("replaced_courier_id", UUID.class),
                r.get("replacement_courier_id", UUID.class), r.get("replaced_leg_km", BigDecimal.class),
                r.get("remaining_km", BigDecimal.class), created == null ? null : created.toInstant(ZoneOffset.UTC));
    }
}
