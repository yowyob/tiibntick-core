package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IPaymentMirrorRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.CashCollectionMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowMirror;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.EscrowShares;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.PaymentIntent;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@link IPaymentMirrorRepository} over {@link DatabaseClient} ({@code billing.wallet_payment_intents},
 * {@code wallet_escrows}, {@code wallet_cash_collections}); joins the caller's reactive transaction. Timestamps are
 * stored in UTC.
 */
@Component
public class PaymentMirrorRepositoryAdapter implements IPaymentMirrorRepository {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final DatabaseClient db;

    public PaymentMirrorRepositoryAdapter(DatabaseClient db) {
        this.db = db;
    }

    // ── intents ─────────────────────────────────────────────────────────────

    @Override
    public Mono<PaymentIntent> findIntent(UUID intentId) {
        return db.sql("SELECT * FROM billing.wallet_payment_intents WHERE id = :id").bind("id", intentId)
                .map(PaymentMirrorRepositoryAdapter::intent).one();
    }

    @Override
    public Mono<PaymentIntent> findIntentByDelivery(UUID deliveryId) {
        return db.sql("SELECT * FROM billing.wallet_payment_intents WHERE delivery_id = :d").bind("d", deliveryId)
                .map(PaymentMirrorRepositoryAdapter::intent).one();
    }

    @Override
    public Mono<PaymentIntent> saveIntent(PaymentIntent i, boolean isNew) {
        DatabaseClient.GenericExecuteSpec spec = isNew
                ? db.sql("INSERT INTO billing.wallet_payment_intents (id, tenant_id, delivery_id, announcement_id,"
                        + " response_id, mode, amount, currency, shares, payer_actor_id, payer_wallet_id,"
                        + " courier_actor_id, courier_wallet_id, platform_wallet_id, expires_at, consumed_at, created_at)"
                        + " VALUES (:id, :tenant, :delivery, :announcement, :response, :mode, :amount, :currency,"
                        + " :shares, :payer, :payerWallet, :courier, :courierWallet, :platformWallet, :expires,"
                        + " :consumed, :created)")
                : db.sql("UPDATE billing.wallet_payment_intents SET tenant_id = :tenant, announcement_id = :announcement,"
                        + " response_id = :response, mode = :mode, amount = :amount, currency = :currency,"
                        + " shares = :shares, payer_actor_id = :payer, payer_wallet_id = :payerWallet,"
                        + " courier_actor_id = :courier, courier_wallet_id = :courierWallet,"
                        + " platform_wallet_id = :platformWallet, consumed_at = :consumed, expires_at = :expires"
                        + " WHERE id = :id AND delivery_id = :delivery");
        {
            spec = bind(spec, "tenant", i.tenantId(), UUID.class);
            spec = spec.bind("delivery", i.deliveryId());
            spec = bind(spec, "announcement", i.announcementId(), UUID.class);
            spec = bind(spec, "response", i.responseId(), UUID.class);
            spec = spec.bind("mode", i.mode().name()).bind("amount", i.amount()).bind("currency", i.currency())
                    .bind("shares", sharesJson(i.shares()));
            spec = bind(spec, "payer", i.payerActorId(), UUID.class);
            spec = bind(spec, "payerWallet", i.payerWalletId(), UUID.class);
            spec = bind(spec, "courier", i.courierActorId(), UUID.class);
            spec = bind(spec, "courierWallet", i.courierWalletId(), UUID.class);
            spec = bind(spec, "platformWallet", i.platformWalletId(), UUID.class);
            if (isNew) {
                spec = spec.bind("created", utc(i.createdAt() != null ? i.createdAt() : Instant.now()));
            }
        }
        spec = spec.bind("id", i.id());
        spec = bind(spec, "expires", utc(i.expiresAt()), LocalDateTime.class);
        spec = bind(spec, "consumed", utc(i.consumedAt()), LocalDateTime.class);
        return spec.fetch().rowsUpdated().thenReturn(i);
    }

    // ── escrows ─────────────────────────────────────────────────────────────

    @Override
    public Mono<EscrowMirror> findEscrow(UUID deliveryId, String purpose) {
        return db.sql("SELECT * FROM billing.wallet_escrows WHERE delivery_id = :d AND purpose = :p")
                .bind("d", deliveryId).bind("p", purpose)
                .map(PaymentMirrorRepositoryAdapter::escrow).one();
    }

    @Override
    public Mono<EscrowMirror> saveEscrow(EscrowMirror e, boolean isNew) {
        DatabaseClient.GenericExecuteSpec spec = isNew
                ? db.sql("INSERT INTO billing.wallet_escrows (id, tenant_id, delivery_id, purpose, kernel_escrow_id,"
                        + " status, amount, currency, payer_wallet_id, reason, created_at, updated_at) VALUES (:id,"
                        + " :tenant, :delivery, :purpose, :kernel, :status, :amount, :currency, :payer, :reason,"
                        + " :created, :updated)")
                        .bind("tenant", e.tenantId()).bind("delivery", e.deliveryId()).bind("purpose", e.purpose())
                        .bind("kernel", e.kernelEscrowId()).bind("amount", e.amount()).bind("currency", e.currency())
                        .bind("created", utc(e.createdAt() != null ? e.createdAt() : Instant.now()))
                : db.sql("UPDATE billing.wallet_escrows SET status = :status, reason = :reason, updated_at = :updated"
                        + " WHERE id = :id");
        spec = spec.bind("id", e.id()).bind("status", e.status().name())
                .bind("updated", utc(e.updatedAt() != null ? e.updatedAt() : Instant.now()));
        spec = bind(spec, "reason", truncate(e.reason()), String.class);
        if (isNew) {
            spec = bind(spec, "payer", e.payerWalletId(), UUID.class);
        }
        return spec.fetch().rowsUpdated().thenReturn(e);
    }

    // ── cash collections ────────────────────────────────────────────────────

    @Override
    public Mono<CashCollectionMirror> findCashCollection(UUID deliveryId) {
        return db.sql("SELECT * FROM billing.wallet_cash_collections WHERE delivery_id = :d").bind("d", deliveryId)
                .map(PaymentMirrorRepositoryAdapter::cash).one();
    }

    @Override
    public Mono<CashCollectionMirror> saveCashCollection(CashCollectionMirror c, boolean isNew) {
        DatabaseClient.GenericExecuteSpec spec = isNew
                ? db.sql("INSERT INTO billing.wallet_cash_collections (id, tenant_id, delivery_id, kernel_collection_id,"
                        + " courier_actor_id, courier_wallet_id, expected_amount, collected_amount, amount_to_remit,"
                        + " currency, status, created_at, updated_at) VALUES (:id, :tenant, :delivery, :kernel,"
                        + " :courier, :courierWallet, :expected, :collected, :toRemit, :currency, :status, :created,"
                        + " :updated)")
                        .bind("tenant", c.tenantId()).bind("delivery", c.deliveryId())
                        .bind("kernel", c.kernelCollectionId()).bind("courier", c.courierActorId())
                        .bind("courierWallet", c.courierWalletId()).bind("expected", c.expectedAmount())
                        .bind("currency", c.currency())
                        .bind("created", utc(c.createdAt() != null ? c.createdAt() : Instant.now()))
                : db.sql("UPDATE billing.wallet_cash_collections SET status = :status, collected_amount = :collected,"
                        + " amount_to_remit = :toRemit, updated_at = :updated WHERE id = :id");
        spec = spec.bind("id", c.id()).bind("status", c.status().name())
                .bind("updated", utc(c.updatedAt() != null ? c.updatedAt() : Instant.now()));
        spec = bind(spec, "collected", c.collectedAmount(), BigDecimal.class);
        spec = bind(spec, "toRemit", c.amountToRemit(), BigDecimal.class);
        return spec.fetch().rowsUpdated().thenReturn(c);
    }

    @Override
    public Flux<CashCollectionMirror> outstandingCashCollections(UUID courierActorId) {
        return db.sql("SELECT * FROM billing.wallet_cash_collections WHERE courier_actor_id = :c"
                        + " AND status IN ('OPEN', 'COLLECTED') ORDER BY created_at")
                .bind("c", courierActorId).map(PaymentMirrorRepositoryAdapter::cash).all();
    }

    // ── mapping ─────────────────────────────────────────────────────────────

    private static PaymentIntent intent(Readable r) {
        return new PaymentIntent(r.get("id", UUID.class), r.get("tenant_id", UUID.class),
                r.get("delivery_id", UUID.class), r.get("announcement_id", UUID.class), r.get("response_id", UUID.class),
                PaymentIntent.Mode.valueOf(r.get("mode", String.class)), r.get("amount", BigDecimal.class),
                r.get("currency", String.class), shares(r.get("shares", String.class), r.get("amount", BigDecimal.class)),
                r.get("payer_actor_id", UUID.class), r.get("payer_wallet_id", UUID.class),
                r.get("courier_actor_id", UUID.class), r.get("courier_wallet_id", UUID.class),
                r.get("platform_wallet_id", UUID.class), instant(r.get("expires_at", LocalDateTime.class)),
                instant(r.get("consumed_at", LocalDateTime.class)), instant(r.get("created_at", LocalDateTime.class)));
    }

    private static EscrowMirror escrow(Readable r) {
        return new EscrowMirror(r.get("id", UUID.class), r.get("tenant_id", UUID.class),
                r.get("delivery_id", UUID.class), r.get("purpose", String.class), r.get("kernel_escrow_id", UUID.class),
                EscrowMirror.Status.valueOf(r.get("status", String.class)), r.get("amount", BigDecimal.class),
                r.get("currency", String.class), r.get("payer_wallet_id", UUID.class), r.get("reason", String.class),
                instant(r.get("created_at", LocalDateTime.class)), instant(r.get("updated_at", LocalDateTime.class)));
    }

    private static CashCollectionMirror cash(Readable r) {
        return new CashCollectionMirror(r.get("id", UUID.class), r.get("tenant_id", UUID.class),
                r.get("delivery_id", UUID.class), r.get("kernel_collection_id", UUID.class),
                r.get("courier_actor_id", UUID.class), r.get("courier_wallet_id", UUID.class),
                r.get("expected_amount", BigDecimal.class), r.get("collected_amount", BigDecimal.class),
                r.get("amount_to_remit", BigDecimal.class), r.get("currency", String.class),
                CashCollectionMirror.Status.valueOf(r.get("status", String.class)),
                instant(r.get("created_at", LocalDateTime.class)), instant(r.get("updated_at", LocalDateTime.class)));
    }

    static String sharesJson(EscrowShares s) {
        ObjectNode node = JSON.createObjectNode();
        node.put("platform", s.platform());
        ArrayNode merchants = node.putArray("merchants");
        s.merchants().forEach(m -> merchants.addObject().put("walletId", m.walletId().toString())
                .put("amount", m.amount()));
        node.put("courier", s.courier());
        return node.toString();
    }

    static EscrowShares shares(String json, BigDecimal price) {
        try {
            JsonNode n = JSON.readTree(json);
            List<EscrowShares.Part> merchants = new ArrayList<>();
            n.path("merchants").forEach(m -> merchants.add(new EscrowShares.Part(
                    UUID.fromString(m.path("walletId").asText()), m.path("amount").decimalValue())));
            return new EscrowShares(price, n.path("platform").decimalValue(), merchants,
                    n.path("courier").decimalValue());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable shares of a payment intent: " + json, e);
        }
    }

    private static <T> DatabaseClient.GenericExecuteSpec bind(DatabaseClient.GenericExecuteSpec spec, String name,
                                                              T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static LocalDateTime utc(Instant i) {
        return i == null ? null : LocalDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime t) {
        return t == null ? null : t.toInstant(ZoneOffset.UTC);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 255 ? s : s.substring(0, 255);
    }
}
