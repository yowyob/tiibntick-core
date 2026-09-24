package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse d'un besoin de livraison.
 *
 * <p><strong>Les trois identifiants et les trois objets coexistent volontairement.</strong>
 * {@code packetId}, {@code pickupAddressId} et {@code deliveryAddressId} restent émis tels quels
 * pour les consommateurs existants (front web) ; {@code packet}, {@code pickupAddress} et
 * {@code deliveryAddress} sont ajoutés pour que le mobile n'ait pas à rappeler
 * {@code GET /api/addresses/{id}} par besoin — et parce qu'aucun endpoint {@code GET} colis
 * n'existe, le colis n'était tout simplement pas lisible autrement.
 *
 * <p>Les trois objets sont {@code null} quand l'identifiant correspondant est {@code null}
 * (colis optionnel) ou quand la ligne référencée est introuvable : l'enrichissement dégrade,
 * il n'échoue pas — un besoin reste lisible même si son adresse a été supprimée.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryNeedResponseDTO {
    private UUID id;
    private UUID userId;
    private UUID packetId;
    private UUID pickupAddressId;
    private UUID deliveryAddressId;
    /** Adresse d'enlèvement résolue, même forme que {@code GET /api/addresses/{id}}. */
    private AddressDTO pickupAddress;
    /** Adresse de livraison résolue, même forme que {@code GET /api/addresses/{id}}. */
    private AddressDTO deliveryAddress;
    /** Colis résolu depuis la table {@code packets} ; {@code null} si le besoin n'en porte pas. */
    private PacketDTO packet;
    private String title;
    private String description;
    private DeliveryNeedStatus status;
    private Integer duration;
    private String signatureUrl;
    private String paymentMethod;
    private String transportMethod;
    private Double distance;
    private UUID deliveryId;
    /** Freelancer retenu par le client (null tant que le besoin est PENDING). */
    private UUID assignedFreelancerId;
    private Instant createdAt;
    private Instant updatedAt;
    private java.time.LocalDateTime pickupDeadline;
}
