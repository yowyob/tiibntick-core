package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PriceCalculationRequest {
    @Builder.Default
    private Double weight = 0.0;
    @Builder.Default
    private Double volumeCbm = 0.0;
    @Builder.Default
    private Boolean isFragile = false;
    @Builder.Default
    private Boolean isPerishable = false;
    @Builder.Default
    private Double distanceKm = 0.0; // Pour le livreur
    @Builder.Default
    private Integer days = 1; // Pour le point relais (1 jour par défaut)

    // Contexte optionnel transmis au moteur de tarification (règles DSL : heure, priorité, attente…)
    private Integer waitingMinutes;
    /** LOW, NORMAL, HIGH, URGENT, CRITICAL (ou STANDARD/EXPRESS). */
    private String priority;
    /** Heure prévue de prise en charge ; défaut : maintenant. */
    private java.time.LocalDateTime scheduledAt;
    private String paymentMethod;
    private Double declaredValueXaf;
}
