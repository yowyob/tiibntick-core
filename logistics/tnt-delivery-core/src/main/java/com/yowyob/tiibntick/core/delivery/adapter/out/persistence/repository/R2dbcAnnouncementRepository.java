package com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryAnnouncementEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Spring Data R2DBC repository for {@code DeliveryAnnouncementEntity}.
 *
 * @author MANFOUO Braun
 */
public interface R2dbcAnnouncementRepository extends ReactiveCrudRepository<DeliveryAnnouncementEntity, UUID> {

    @Query("SELECT * FROM tnt_delivery_announcements WHERE tenant_id = :tenantId AND id = :id")
    Mono<DeliveryAnnouncementEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("SELECT * FROM tnt_delivery_announcements WHERE tenant_id = :tenantId AND client_id = :clientId ORDER BY created_at DESC")
    Flux<DeliveryAnnouncementEntity> findByTenantIdAndClientId(UUID tenantId, UUID clientId);

    @Query("SELECT * FROM tnt_delivery_announcements WHERE tenant_id = :tenantId AND status = :status ORDER BY created_at DESC")
    Flux<DeliveryAnnouncementEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    @Query("""
            SELECT * FROM tnt_delivery_announcements
            WHERE tenant_id = :tenantId
              AND status IN ('PUBLISHED','IN_NEGOTIATION')
            ORDER BY created_at DESC
            """)
    Flux<DeliveryAnnouncementEntity> findOpenByTenantId(UUID tenantId);

    /**
     * Annonces ouvertes visibles d'un livreur, filtrées en base (pas d'index géo sur
     * cette table : boîte englobante sur {@code pickup_latitude/longitude} puis distance du grand
     * cercle, R = 6371 km).
     *
     * <ul>
     *   <li>livreur avec position ({@code hasPoint}) : annonce avec coordonnées → distance ≤ rayon ;
     *       annonce sans coordonnées → même ville si la ville du livreur est connue, sinon visible
     *       (rien ne permet de l'exclure) ;</li>
     *   <li>livreur sans position, ville connue : même ville ;</li>
     *   <li>ni l'un ni l'autre : l'appelant ne passe pas par cette requête (tout est visible).</li>
     * </ul>
     * {@code city} arrive déjà normalisée (minuscules, sans accents, sans espaces de bord) ;
     * {@code ''} = inconnue. La colonne est normalisée de la même façon par {@code translate}.
     */
    @Query("""
            SELECT * FROM tnt_delivery_announcements
            WHERE tenant_id = :tenantId
              AND status IN ('PUBLISHED','IN_NEGOTIATION')
              AND (
                (:hasPoint
                   AND pickup_latitude IS NOT NULL AND pickup_longitude IS NOT NULL
                   AND pickup_latitude BETWEEN :minLat AND :maxLat
                   AND pickup_longitude BETWEEN :minLng AND :maxLng
                   AND 6371.0 * acos(LEAST(1.0, GREATEST(-1.0,
                         cos(radians(:lat)) * cos(radians(pickup_latitude))
                           * cos(radians(pickup_longitude) - radians(:lng))
                         + sin(radians(:lat)) * sin(radians(pickup_latitude))))) <= :radiusKm)
                OR (:city <> ''
                   AND (NOT :hasPoint OR pickup_latitude IS NULL OR pickup_longitude IS NULL)
                   AND translate(lower(trim(pickup_city)),
                         'àáâäãåèéêëìíîïòóôöõùúûüçñ', 'aaaaaaeeeeiiiiooooouuuucn') = :city)
                OR (:hasPoint AND :city = ''
                   AND (pickup_latitude IS NULL OR pickup_longitude IS NULL))
              )
            ORDER BY created_at DESC
            """)
    Flux<DeliveryAnnouncementEntity> findOpenNear(UUID tenantId, boolean hasPoint, double lat, double lng,
                                                  double minLat, double maxLat, double minLng, double maxLng,
                                                  double radiusKm, String city);
}
