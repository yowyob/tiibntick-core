package com.yowyob.tiibntick.core.gofp.adapter.out.persistence.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Entité de persistence R2DBC pour l'abonnement d'un point relais.
 *
 * Table : gofp.relay_hub_subscriptions
 *
 * @author TiiBnTickTeam
 * @date 10/07/2026
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
@Table("gofp.relay_hub_subscriptions")
public class RelayHubSubscriptionEntity {

    @Id
    private UUID id;

    /** FK → tnt-geo-core.relay_hubs.id */
    @Column("relay_hub_id")
    private UUID relayHubId;

    /** FREE | STANDARD | PREMIUM */
    @Column("subscription_type")
    private String subscriptionType;

    /** ACTIVE | SUSPENDED | EXPIRED | CANCELLED */
    private String status;

    @Column("start_date")
    private Instant startDate;

    @Column("end_date")
    private Instant endDate;

    /** Prix mensuel payé (FCFA). 0 pour le plan FREE. */
    private Double price;

    @Column("payment_method")
    private String paymentMethod;

    /**
     * Capacité maximale de colis simultanés.
     * NULL = illimité (plan PREMIUM).
     */
    @Column("max_packets_simultaneous")
    private Integer maxPacketsSimultaneous;

    /** Nombre de colis actuellement stockés dans le point relais. */
    @Column("packets_used")
    private Integer packetsUsed;

    /**
     * Taux de commission TiiBnTick sur les frais de gardiennage (%).
     * Synchronisé avec le plan au moment de la souscription.
     */
    @Column("commission_percent")
    private Double commissionPercent;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
