package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AddressDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.DeliveryNeedRequestDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.DeliveryNeedResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.response.FreelancerCandidateDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryNeedRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.usecase.TopsisRankingUseCase;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.DeliveryNeed;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AddressUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.AdminRelayPointUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.DeliveryNeedUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.in.FreelancerPricingPolicyUseCase;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.PushNotificationPort;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ResourceNotFoundException;
import com.yowyob.tiibntick.core.gofreelancer.domain.exception.ValidationException;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.freelancer.FreelancerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service implementing the DeliveryNeedUseCase.
 * Handles delivery need lifecycle including assignment and relay point notifications.
 *
 * @author MANFOUO BRAUN
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryNeedApplicationService implements DeliveryNeedUseCase {

    // TODO(pricing): valeurs provisoires, à remplacer par le barème plateforme officiel
    private static final double DEFAULT_BASE_FEE = 500.0;
    private static final double DEFAULT_PRICE_PER_KM = 100.0;
    private static final double DEFAULT_PRICE_PER_KG = 50.0;
    private static final double DEFAULT_PRICE_PER_CBM = 2000.0;
    private static final double DEFAULT_FRAGILE_SURCHARGE = 500.0;
    private static final double DEFAULT_PERISHABLE_SURCHARGE = 500.0;
    private static final String DEFAULT_CURRENCY = "FCFA";

    /** Barème de repli quand le freelancer n'a pas (encore) publié sa politique de prix. */
    private static final FreelancerPricingDTO DEFAULT_POLICY = FreelancerPricingDTO.builder()
            .baseFee(DEFAULT_BASE_FEE)
            .pricePerKm(DEFAULT_PRICE_PER_KM)
            .pricePerKg(DEFAULT_PRICE_PER_KG)
            .pricePerCbm(DEFAULT_PRICE_PER_CBM)
            .fragileSurcharge(DEFAULT_FRAGILE_SURCHARGE)
            .perishableSurcharge(DEFAULT_PERISHABLE_SURCHARGE)
            .currency(DEFAULT_CURRENCY)
            .build();

    private final IDeliveryNeedRepository deliveryNeedRepository;
    private final AdminRelayPointUseCase adminRelayPointUseCase;
    private final PushNotificationPort pushNotificationPort;
    private final com.yowyob.tiibntick.core.gofreelancer.application.usecase.MatchingUseCase matchingUseCase;
    private final AddressUseCase addressUseCase;
    private final GofpUserRepository gofpUserRepository;
    private final GofpFreelancerRepository gofpFreelancerRepository;
    private final FreelancerPricingPolicyUseCase freelancerPricingPolicyUseCase;
    private final DatabaseClient databaseClient;

    /**
     * Active la garde d'ownership. Défaut {@code true} : un déploiement qui oublie la variable
     * doit être protégé, pas ouvert — la configuration dangereuse ne doit jamais être celle
     * obtenue par omission. Le profil {@code test} la met à {@code false}.
     *
     * <p>Attention, ce n'est pas un interrupteur suffisant : la garde ne mord que sur les
     * requêtes authentifiées (cf. {@link #resolveOwner}).
     */
    @Value("${tnt.gofp.ownership-guard.enabled:true}")
    private boolean ownershipGuardEnabled = true;

    /**
     * Identité effective du propriétaire d'un besoin, ou {@code null} si la garde est désactivée.
     *
     * <p>Une seule condition neutralise la garde : le flag {@code tnt.gofp.ownership-guard.enabled}
     * à {@code false} — désactivation explicite, assumée par la configuration. En production ce flag
     * est interdit (cf. {@code GoFreelancerPointCoreConfig#validateOwnershipGuardNotDisabledInProd}).
     *
     * <p>{@code callerId == null} n'est plus un cas de neutralisation. Un appelant authentifié
     * dont le JWT {@code sub} n'est pas un UUID valide (jeton client plate-forme, clé d'API
     * passerelle) obtient {@code userId = null} dans {@code TntSecurityContextService#parseUuidOrNull}
     * — le service lui interdit l'accès plutôt que de laisser passer silencieusement.
     *
     * @throws AccessDeniedException si la garde est active et {@code callerId} est {@code null}
     */
    private UUID resolveOwner(UUID callerId, String operation) {
        if (!ownershipGuardEnabled) {
            return null;
        }
        if (callerId == null) {
            throw new AccessDeniedException(
                    "[ownership] " + operation + " requires an authenticated identity "
                    + "(callerId is null — likely a platform-client token with a non-UUID sub)");
        }
        return callerId;
    }

    /**
     * Refuse l'accès quand le besoin existe mais n'appartient pas à l'appelant.
     *
     * <p>L'ordre compte : l'appelant a déjà été résolu <em>après</em> le chargement du besoin,
     * de sorte qu'un id inexistant sorte en 404 et jamais en 403 — un 403 sur un besoin absent
     * confirmerait son inexistence par différence, et un 404 sur un besoin existant mais
     * étranger empêcherait de distinguer un bug d'un refus légitime côté BFF.
     */
    private Mono<DeliveryNeed> requireOwnership(DeliveryNeed need, UUID callerId, String operation) {
        UUID owner = resolveOwner(callerId, operation);
        if (owner == null || owner.equals(need.getUserId())) {
            return Mono.just(need);
        }
        log.warn("[ownership] {} refusé : caller {} != owner {} (deliveryNeed {})",
                operation, owner, need.getUserId(), need.getId());
        return Mono.error(new AccessDeniedException(
                "This delivery need belongs to another user"));
    }

    @Override
    public Mono<DeliveryNeedResponseDTO> createDeliveryNeed(DeliveryNeedRequestDTO request, UUID callerId) {
        if (request == null) {
            return Mono.error(new IllegalArgumentException("Delivery need request is required"));
        }
        // Volet C — l'identité du propriétaire est dérivée du jeton, jamais du corps de requête :
        // sinon n'importe quel appelant crée un besoin au nom d'autrui. Le userId du body reste
        // toléré tant que la garde est neutralisée (run local sans JWT), et il est ignoré sinon.
        UUID owner = resolveOwner(callerId, "createDeliveryNeed");
        if (owner != null) {
            if (request.getUserId() != null && !owner.equals(request.getUserId())) {
                log.warn("[ownership] createDeliveryNeed : userId du body ({}) ignoré au profit "
                        + "de l'identité du jeton ({})", request.getUserId(), owner);
            }
            request.setUserId(owner);
        }
        if (request.getUserId() == null) {
            return Mono.error(new IllegalArgumentException("userId is required"));
        }
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            return Mono.error(new IllegalArgumentException("title is required"));
        }
        if (request.getPickupAddress() == null || request.getPickupAddress().getAddress() == null) {
            return Mono.error(new IllegalArgumentException("pickupAddress is required"));
        }
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().getAddress() == null) {
            return Mono.error(new IllegalArgumentException("deliveryAddress is required"));
        }

        Mono<AddressDTO> pickupMono = addressUseCase.createAddress(request.getPickupAddress());
        Mono<AddressDTO> deliveryMono = addressUseCase.createAddress(request.getDeliveryAddress());

        return ensureLegacyUser(request.getUserId())
                .then(Mono.zip(pickupMono, deliveryMono))
                .flatMap(addresses -> persistPacketIfPresent(request.getPacket())
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .flatMap(packetId -> {
                            Instant now = Instant.now();
                            DeliveryNeed need = DeliveryNeed.builder()
                                    .id(UUID.randomUUID())
                                    .userId(request.getUserId())
                                    .packetId(packetId.orElse(null))
                                    .title(request.getTitle().trim())
                                    .description(request.getDescription())
                                    .status(DeliveryNeedStatus.PENDING)
                                    .pickupAddressId(addresses.getT1().getId())
                                    .deliveryAddressId(addresses.getT2().getId())
                                    .signatureUrl(request.getSignatureUrl())
                                    .paymentMethod(request.getPaymentMethod())
                                    .transportMethod(request.getTransportMethod())
                                    .distance(request.getDistance())
                                    .duration(request.getDuration())
                                    .pickupDeadline(request.getPickupDeadline())
                                    .targetRelayPointId(request.getTargetRelayPointId())
                                    .requestedStorageDays(request.getRequestedStorageDays())
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build();
                            log.info("Creating DeliveryNeed {} for user {} (packetId={})",
                                    need.getId(), need.getUserId(), need.getPacketId());
                            return deliveryNeedRepository.save(need);
                        }))
                .flatMap(this::enrich);
    }

    /**
     * Persists the optional packet carried by the request into the legacy {@code packets}
     * table and returns its generated id, so the caller can wire it onto
     * {@code delivery_needs.packet_id} (nullable FK, {@code ON DELETE SET NULL}).
     *
     * <p>There is no {@code Packet} entity/repository on the Java side, hence the raw
     * {@link DatabaseClient} — same pattern as {@link #ensureLegacyUser(UUID)}.
     *
     * <p>{@code packets.announcement_id} is deliberately absent from the INSERT: changeset
     * {@code 034-fix-packet-circular-dependency} dropped that column and no later changeset
     * re-adds it.
     *
     * @return the new packet id, or an empty {@link Mono} when no packet was submitted.
     */
    private Mono<UUID> persistPacketIfPresent(PacketDTO packet) {
        if (packet == null) {
            return Mono.empty();
        }

        UUID packetId = UUID.randomUUID();
        String title = (packet.getDesignation() != null && !packet.getDesignation().isBlank())
                ? packet.getDesignation().trim()
                : "Colis";

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                        INSERT INTO packets (id, title, designation, description, weight, height,
                                             width, length, thickness, is_fragile, is_perishable, photo_url)
                        VALUES (:id, :title, :designation, :description, :weight, :height,
                                :width, :length, :thickness, :isFragile, :isPerishable, :photoUrl)
                        """)
                .bind("id", packetId)
                .bind("title", title);

        spec = bindNullable(spec, "designation", packet.getDesignation(), String.class);
        spec = bindNullable(spec, "description", packet.getDescription(), String.class);
        spec = bindNullable(spec, "weight", packet.getWeight(), Double.class);
        spec = bindNullable(spec, "height", packet.getHeight(), Double.class);
        spec = bindNullable(spec, "width", packet.getWidth(), Double.class);
        spec = bindNullable(spec, "length", packet.getLength(), Double.class);
        spec = bindNullable(spec, "thickness", packet.getThickness(), Double.class);
        spec = bindNullable(spec, "isFragile", packet.getFragile(), Boolean.class);
        spec = bindNullable(spec, "isPerishable", packet.getIsPerishable(), Boolean.class);
        spec = bindNullable(spec, "photoUrl", packet.getPhotoPacket(), String.class);

        log.info("Persisting packet {} (title={})", packetId, title);

        return spec.fetch().rowsUpdated().thenReturn(packetId);
    }

    /**
     * {@code DatabaseClient#bind} throws on a {@code null} value — every optional column has
     * to go through {@code bindNull} with its target type instead.
     */
    private static DatabaseClient.GenericExecuteSpec bindNullable(
            DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, type);
    }

    /**
     * {@code delivery_needs.user_id} FKs {@code users(id)}. Ensure a legacy row exists
     * (from GofpUser when available, otherwise a placeholder).
     *
     * @author MANFOUO BRAUN
     */
    private Mono<Void> ensureLegacyUser(UUID userId) {
        return databaseClient.sql("SELECT 1 FROM users WHERE id = :id")
                .bind("id", userId)
                .map((row, meta) -> 1)
                .first()
                .flatMap(exists -> Mono.<Void>empty())
                .switchIfEmpty(Mono.defer(() ->
                        gofpUserRepository.findByCoreUserId(userId)
                                .defaultIfEmpty(com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpUser.builder()
                                        .firstName("User")
                                        .lastName(userId.toString().substring(0, 8))
                                        .build())
                                .flatMap(u -> databaseClient.sql("""
                                                INSERT INTO users (id, first_name, last_name, password)
                                                VALUES (:id, :fn, :ln, :pw)
                                                ON CONFLICT (id) DO NOTHING
                                                """)
                                        .bind("id", userId)
                                        .bind("fn", u.getFirstName() != null ? u.getFirstName() : "User")
                                        .bind("ln", u.getLastName() != null ? u.getLastName() : "GOFP")
                                        .bind("pw", "{noop}placeholder")
                                        .fetch()
                                        .rowsUpdated()
                                        .then())
                ));
    }

    @Override
    public Mono<DeliveryNeedResponseDTO> getDeliveryNeed(UUID id, UUID callerId) {
        return deliveryNeedRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + id)))
                .flatMap(need -> requireOwnership(need, callerId, "getDeliveryNeed"))
                .flatMap(this::enrich);
    }

    @Override
    public Flux<DeliveryNeedResponseDTO> getDeliveryNeedsByUserId(UUID userId, UUID callerId) {
        // Pas de besoin à charger ici : la comparaison porte directement sur le path variable.
        UUID owner = resolveOwner(callerId, "getDeliveryNeedsByUserId");
        if (owner != null && !owner.equals(userId)) {
            log.warn("[ownership] getDeliveryNeedsByUserId refusé : caller {} != {}", owner, userId);
            return Flux.error(new AccessDeniedException("Cannot list another user's delivery needs"));
        }
        return listDeliveryNeedsByUserId(userId);
    }

    private Flux<DeliveryNeedResponseDTO> listDeliveryNeedsByUserId(UUID userId) {
        return enrichAll(deliveryNeedRepository.findAllByUserId(userId));
    }

    @Override
    public Mono<Void> deleteDeliveryNeed(UUID id, UUID callerId) {
        return deliveryNeedRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + id)))
                .flatMap(need -> requireOwnership(need, callerId, "deleteDeliveryNeed"))
                .flatMap(need -> deliveryNeedRepository.deleteById(id));
    }

    /**
     * Valide que {@code freelancerId} désigne un freelancer réel et assignable.
     *
     * <p>L'id attendu est l'id « fil » {@code gofp_freelancers.id} — le même que celui
     * produit par le matching et renvoyé par {@code GET /{id}/candidates}. Sans ce contrôle,
     * un id inexistant remonte en {@code 500} brut depuis la FK
     * {@code fk_delivery_needs_assigned_freelancer} (changeset 070), illisible pour l'appelant.
     *
     * <p>On ne vérifie <em>pas</em> que le freelancer figure parmi les candidats éligibles :
     * cela imposerait de rejouer le matching à chaque assignation, dont le résultat n'est pas
     * déterministe (les positions GPS bougent entre {@code /candidates} et {@code /assign}) —
     * un choix légitime du client se verrait alors refusé.
     */
    private Mono<GofpFreelancer> requireAssignableFreelancer(UUID freelancerId) {
        if (freelancerId == null) {
            return Mono.error(new ValidationException("freelancerId is required"));
        }
        return gofpFreelancerRepository.findById(freelancerId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException(
                        "Freelancer", "id", freelancerId)))
                .flatMap(freelancer -> {
                    if (freelancer.getStatus() != FreelancerStatus.APPROVED) {
                        return Mono.error(new IllegalStateException(
                                "Freelancer " + freelancerId + " is not assignable: status="
                                        + freelancer.getStatus() + " (expected APPROVED)"));
                    }
                    if (!Boolean.TRUE.equals(freelancer.getIsActive())) {
                        return Mono.error(new IllegalStateException(
                                "Freelancer " + freelancerId + " is not assignable: inactive"));
                    }
                    return Mono.just(freelancer);
                });
    }

    /**
     * <p><strong>Ordre des contrôles.</strong> Le besoin est résolu et sa propriété vérifiée
     * <em>avant</em> {@link #requireAssignableFreelancer} : un appelant étranger ne doit rien
     * apprendre du parc de freelancers (existence, statut) en sondant un besoin qui ne lui
     * appartient pas. La séquence est donc 404 besoin → 403 besoin → 400/404/409 freelancer.
     * L'inverse — l'ordre d'origine — reste fonctionnellement correct mais fuit de l'information
     * au premier appelant venu.
     */
    @Override
    public Mono<DeliveryNeedResponseDTO> assignFreelancer(UUID deliveryNeedId, UUID freelancerId, UUID callerId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("DeliveryNeed not found: " + deliveryNeedId)))
                .flatMap(need -> requireOwnership(need, callerId, "assignFreelancer"))
                .flatMap(need -> requireAssignableFreelancer(freelancerId).thenReturn(need))
                .flatMap(need -> {
                    need.setAssignedFreelancerId(freelancerId);
                    need.setStatus(DeliveryNeedStatus.ASSIGNED);
                    need.setUpdatedAt(Instant.now());

                    log.info("Assigning freelancer {} to DeliveryNeed {}", freelancerId, deliveryNeedId);

                    Mono<DeliveryNeed> saveNeed = deliveryNeedRepository.save(need);

                    if (need.getTargetRelayPointId() != null) {
                        Mono<Void> notifyRelayPoint = adminRelayPointUseCase.getRelayPointDetails(need.getTargetRelayPointId())
                                .flatMap(relayPoint -> pushNotificationPort.sendPushNotification(
                                        relayPoint.getFreelancerId(),
                                        "Nouvelle Livraison Prévue !",
                                        "Un livreur vient d'accepter une course à destination de votre point relais. " +
                                                "Préparez-vous à recevoir le colis."
                                ))
                                .onErrorResume(e -> {
                                    log.error("Failed to notify relay point owner upon assignment", e);
                                    return Mono.empty();
                                });

                        return saveNeed.flatMap(saved -> notifyRelayPoint.thenReturn(saved));
                    }

                    return saveNeed;
                })
                .flatMap(this::enrich);
    }

    @Override
    public Flux<FreelancerCandidateDTO> getCandidatesWithPricing(UUID deliveryNeedId, UUID callerId) {
        return deliveryNeedRepository.findById(deliveryNeedId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Delivery need not found: " + deliveryNeedId)))
                .flatMap(need -> requireOwnership(need, callerId, "getCandidatesWithPricing"))
                .flatMapMany(need -> Mono.zip(
                                loadCoordinates(need.getPickupAddressId()),
                                loadCoordinates(need.getDeliveryAddressId()),
                                loadPacketMetrics(need.getPacketId()))
                        .flatMapMany(ctx -> {
                            GeoPoint pickup = ctx.getT1();
                            GeoPoint delivery = ctx.getT2();
                            PacketMetrics packet = ctx.getT3();
                            double distanceKm = need.getDistance() != null ? need.getDistance() : 0.0;

                            log.info("Matching DeliveryNeed {}: pickup=({},{}) delivery=({},{}) "
                                            + "distance={}km weight={}kg volume={}m3 fragile={} perishable={}",
                                    need.getId(), pickup.lat(), pickup.lon(), delivery.lat(), delivery.lon(),
                                    distanceKm, packet.weightKg(), packet.volumeM3(),
                                    packet.fragile(), packet.perishable());

                            return matchingUseCase.processMatchingForDeliveryNeed(
                                            need.getId(), pickup.lat(), pickup.lon(),
                                            delivery.lat(), delivery.lon(),
                                            packet.volumeM3(), need.getPickupDeadline())
                                    .flatMapMany(Flux::fromIterable)
                                    .flatMap(candidate -> toPricedCandidate(candidate, distanceKm, packet));
                        }));
    }

    /**
     * Assemble un candidat : politique de prix du freelancer (ou {@link #DEFAULT_POLICY}),
     * identité réelle (ou repli {@code "Freelancer"}), prix estimé et détail lisible.
     *
     * <p><strong>Trois espaces d'identité cohabitent</strong> et ne doivent pas être confondus :
     * <ul>
     *   <li>{@code candidate.freelancerId} = {@code gofp_freelancers.id} — la clé « fil »
     *       produite par le matching ({@code FreelancerProviderAdapter#fromGofp});</li>
     *   <li>le nom se lit via {@code gofp_users.core_user_id} =
     *       {@code GofpFreelancer#getCoreUserId()};</li>
     *   <li>le tarif se lit via {@code delivery_person_pricing.delivery_person_id}
     *       (FK {@code delivery_persons(id)}) = {@code GofpFreelancer#getCoreFreelancerId()}.</li>
     * </ul>
     * On charge donc la ligne {@code gofp_freelancers} une seule fois pour traduire l'id fil
     * dans les deux autres espaces. Sans cette traduction, le nom retombe systématiquement sur
     * {@code "Freelancer"} et le tarif sur {@link #DEFAULT_POLICY}, silencieusement.
     */
    private Mono<FreelancerCandidateDTO> toPricedCandidate(
            TopsisRankingUseCase.FreelancerCandidate candidate, double distanceKm, PacketMetrics packet) {

        UUID freelancerId = candidate.getFreelancerId();

        return gofpFreelancerRepository.findById(freelancerId)
                .switchIfEmpty(Mono.defer(() -> gofpFreelancerRepository.findByCoreFreelancerId(freelancerId)))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(e -> {
                    log.warn("GofpFreelancer {} lookup failed — pricing/name fall back to defaults",
                            freelancerId, e);
                    return Mono.just(Optional.empty());
                })
                .flatMap(freelancer -> priceCandidate(candidate, freelancer.orElse(null), distanceKm, packet));
    }

    private Mono<FreelancerCandidateDTO> priceCandidate(
            TopsisRankingUseCase.FreelancerCandidate candidate,
            GofpFreelancer freelancer,
            double distanceKm, PacketMetrics packet) {

        UUID freelancerId = candidate.getFreelancerId();
        // Repli sur l'id fil quand la ligne gofp_freelancers est introuvable : au pire on
        // retombe sur les valeurs par défaut, jamais sur une exception.
        UUID pricingId = (freelancer != null && freelancer.getCoreFreelancerId() != null)
                ? freelancer.getCoreFreelancerId() : freelancerId;
        UUID identityId = (freelancer != null && freelancer.getCoreUserId() != null)
                ? freelancer.getCoreUserId() : freelancerId;

        Mono<FreelancerPricingDTO> policyMono = freelancerPricingPolicyUseCase.getPolicy(pricingId)
                .defaultIfEmpty(DEFAULT_POLICY)
                .onErrorResume(e -> {
                    log.warn("Pricing policy lookup failed for delivery person {} — falling back to default barème",
                            pricingId, e);
                    return Mono.just(DEFAULT_POLICY);
                });

        Mono<String[]> nameMono = gofpUserRepository.findByCoreUserId(identityId)
                .map(u -> new String[]{
                        (u.getFirstName() != null && !u.getFirstName().isBlank()) ? u.getFirstName() : "Freelancer",
                        u.getLastName() != null ? u.getLastName() : ""})
                .defaultIfEmpty(new String[]{"Freelancer", ""})
                .onErrorReturn(new String[]{"Freelancer", ""});

        return Mono.zip(policyMono, nameMono)
                .map(t -> {
                    FreelancerPricingDTO policy = t.getT1();
                    String[] name = t.getT2();

                    double baseFee = orDefault(policy.getBaseFee(), DEFAULT_BASE_FEE);
                    double perKm = orDefault(policy.getPricePerKm(), DEFAULT_PRICE_PER_KM);
                    double perKg = orDefault(policy.getPricePerKg(), DEFAULT_PRICE_PER_KG);
                    double perCbm = orDefault(policy.getPricePerCbm(), DEFAULT_PRICE_PER_CBM);
                    double fragileFee = packet.fragile()
                            ? orDefault(policy.getFragileSurcharge(), DEFAULT_FRAGILE_SURCHARGE) : 0.0;
                    double perishableFee = packet.perishable()
                            ? orDefault(policy.getPerishableSurcharge(), DEFAULT_PERISHABLE_SURCHARGE) : 0.0;
                    String currency = (policy.getCurrency() != null && !policy.getCurrency().isBlank())
                            ? policy.getCurrency() : DEFAULT_CURRENCY;

                    double distanceCost = perKm * distanceKm;
                    double weightCost = perKg * packet.weightKg();
                    double volumeCost = perCbm * packet.volumeM3();
                    double total = round2(baseFee + distanceCost + weightCost + volumeCost
                            + fragileFee + perishableFee);

                    StringBuilder breakdown = new StringBuilder("Base ").append(num(baseFee));
                    if (distanceCost != 0.0) {
                        breakdown.append(" + ").append(num(distanceKm)).append("km×").append(num(perKm));
                    }
                    if (weightCost != 0.0) {
                        breakdown.append(" + ").append(num(packet.weightKg())).append("kg×").append(num(perKg));
                    }
                    if (volumeCost != 0.0) {
                        breakdown.append(" + ").append(num(packet.volumeM3())).append("m³×").append(num(perCbm));
                    }
                    if (fragileFee != 0.0) {
                        breakdown.append(" + fragile ").append(num(fragileFee));
                    }
                    if (perishableFee != 0.0) {
                        breakdown.append(" + périssable ").append(num(perishableFee));
                    }
                    breakdown.append(" = ").append(num(total)).append(' ').append(currency);

                    return FreelancerCandidateDTO.builder()
                            .freelancerId(freelancerId)
                            .firstName(name[0])
                            .lastName(name[1])
                            .rating(candidate.getRating())
                            .estimatedPrice(total)
                            .priceBreakdown(breakdown.toString())
                            .build();
                });
    }

    /** Coordonnées d'une adresse ; {@code (0,0)} si l'adresse ou ses coordonnées sont absentes. */
    private Mono<GeoPoint> loadCoordinates(UUID addressId) {
        if (addressId == null) {
            return Mono.just(GeoPoint.ORIGIN);
        }
        return addressUseCase.getAddressById(addressId)
                .map(dto -> {
                    Address address = dto.getAddress();
                    if (address == null) {
                        return GeoPoint.ORIGIN;
                    }
                    return address.getCoordinates()
                            .map(c -> new GeoPoint(c.getLatitude(), c.getLongitude()))
                            .orElse(GeoPoint.ORIGIN);
                })
                .defaultIfEmpty(GeoPoint.ORIGIN)
                .onErrorResume(e -> {
                    log.warn("Address {} lookup failed — falling back to (0,0)", addressId, e);
                    return Mono.just(GeoPoint.ORIGIN);
                });
    }

    /**
     * Métriques du colis lues directement dans {@code packets} (aucune entité côté Java).
     * Unités en base : {@code weight} en kg, {@code length/width/height} en cm — d'où la
     * conversion en m³ par 1e6.
     */
    private Mono<PacketMetrics> loadPacketMetrics(UUID packetId) {
        if (packetId == null) {
            return Mono.just(PacketMetrics.EMPTY);
        }
        return databaseClient.sql("""
                        SELECT weight, length, width, height, is_fragile, is_perishable
                        FROM packets WHERE id = :id
                        """)
                .bind("id", packetId)
                .map((row, meta) -> {
                    double weightKg = orDefault(row.get("weight", Double.class), 0.0);
                    double lengthCm = orDefault(row.get("length", Double.class), 0.0);
                    double widthCm = orDefault(row.get("width", Double.class), 0.0);
                    double heightCm = orDefault(row.get("height", Double.class), 0.0);
                    double volumeM3 = (lengthCm * widthCm * heightCm) / 1_000_000.0;
                    return new PacketMetrics(
                            weightKg,
                            volumeM3,
                            Boolean.TRUE.equals(row.get("is_fragile", Boolean.class)),
                            Boolean.TRUE.equals(row.get("is_perishable", Boolean.class)));
                })
                .one()
                .defaultIfEmpty(PacketMetrics.EMPTY)
                .onErrorResume(e -> {
                    log.warn("Packet {} lookup failed — pricing without packet metrics", packetId, e);
                    return Mono.just(PacketMetrics.EMPTY);
                });
    }

    /** Latitude/longitude déjà résolues, jamais {@code null}. */
    private record GeoPoint(double lat, double lon) {
        private static final GeoPoint ORIGIN = new GeoPoint(0.0, 0.0);
    }

    /** Poids (kg), volume (m³) et suppléments applicables du colis. */
    private record PacketMetrics(double weightKg, double volumeM3, boolean fragile, boolean perishable) {
        private static final PacketMetrics EMPTY = new PacketMetrics(0.0, 0.0, false, false);
    }

    private static double orDefault(Double value, double fallback) {
        return value != null ? value : fallback;
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    /** Formatage compact pour {@code priceBreakdown} : 500 plutôt que 500.0, 0.024 plutôt que 0.024000. */
    private static String num(double value) {
        return BigDecimal.valueOf(value)
                .setScale(6, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /**
     * Réponse enrichie : les deux adresses et le colis résolus <em>en une passe</em>.
     *
     * <p>Les trois lectures partent en parallèle sous un {@link Mono#zip} plutôt qu'en
     * {@code flatMap} chaînés : la latence d'un besoin est celle de la plus lente des trois,
     * pas leur somme. Les identifiants sont déjà portés par le besoin, aucun aller-retour
     * supplémentaire n'est nécessaire pour les découvrir.
     *
     * <p><strong>Court-circuit sur {@code null}.</strong> Un identifiant absent ne déclenche
     * aucune requête — c'est ce qui garde les besoins sans colis (le colis est optionnel à la
     * création) au même coût qu'avant.
     *
     * <p><strong>Portée honnête.</strong> Sur les routes qui renvoient un {@code Flux}, cela
     * reste 3 lectures <em>par besoin</em> : le zip supprime la sérialisation à l'intérieur d'un
     * besoin, pas la multiplication entre besoins. Un vrai chargement par lot supposerait un
     * {@code IN (...)} sur {@code addresses} et {@code packets}, donc une méthode de dépôt qui
     * n'existe pas encore. Voir {@link #enrichAll(Flux)} pour ce qui est fait en attendant.
     */
    private Mono<DeliveryNeedResponseDTO> enrich(DeliveryNeed need) {
        return Mono.zip(
                        loadAddress(need.getPickupAddressId()),
                        loadAddress(need.getDeliveryAddressId()),
                        loadPacket(need.getPacketId()))
                .map(ctx -> {
                    DeliveryNeedResponseDTO dto = mapToResponse(need);
                    dto.setPickupAddress(ctx.getT1().orElse(null));
                    dto.setDeliveryAddress(ctx.getT2().orElse(null));
                    dto.setPacket(ctx.getT3().orElse(null));
                    return dto;
                });
    }

    /**
     * Enrichit un flux de besoins avec {@link Flux#flatMapSequential} : les besoins sont
     * enrichis concurremment mais réémis dans l'ordre d'arrivée. Un {@code concatMap} aurait
     * sérialisé les lectures (N × latence), un {@code flatMap} aurait rendu l'ordre de la liste
     * non déterministe d'un appel à l'autre — inacceptable pour une liste affichée.
     */
    private Flux<DeliveryNeedResponseDTO> enrichAll(Flux<DeliveryNeed> needs) {
        return needs.flatMapSequential(this::enrich);
    }

    /**
     * Adresse résolue, ou {@link Optional#empty()} si l'identifiant est {@code null}, si la ligne
     * n'existe plus, ou si la lecture échoue. L'enrichissement ne doit jamais faire échouer la
     * lecture du besoin lui-même : le contrat historique (les identifiants) reste servi.
     */
    private Mono<Optional<AddressDTO>> loadAddress(UUID addressId) {
        if (addressId == null) {
            return Mono.just(Optional.empty());
        }
        return addressUseCase.getAddressById(addressId)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(e -> {
                    log.warn("Address {} lookup failed — response served without the full address",
                            addressId, e);
                    return Mono.just(Optional.empty());
                });
    }

    /**
     * Colis résolu directement dans {@code packets} : il n'existe ni entité ni dépôt Java pour
     * cette table (même raison que {@link #loadPacketMetrics(UUID)} et
     * {@link #persistPacketIfPresent(PacketDTO)}), et aucun endpoint {@code GET /api/packets/{id}}
     * — sans cette lecture, le colis est invisible pour tout consommateur.
     *
     * <p>Les noms de colonnes suivent l'INSERT de {@link #persistPacketIfPresent(PacketDTO)} :
     * {@code is_fragile}/{@code photo_url} en base, {@code fragile}/{@code photoPacket} dans le DTO.
     */
    private Mono<Optional<PacketDTO>> loadPacket(UUID packetId) {
        if (packetId == null) {
            return Mono.just(Optional.empty());
        }
        return databaseClient.sql("""
                        SELECT designation, description, weight, height, width, length,
                               thickness, is_fragile, is_perishable, photo_url
                        FROM packets WHERE id = :id
                        """)
                .bind("id", packetId)
                .map((row, meta) -> {
                    PacketDTO dto = new PacketDTO();
                    dto.setDesignation(row.get("designation", String.class));
                    dto.setDescription(row.get("description", String.class));
                    dto.setWeight(row.get("weight", Double.class));
                    dto.setHeight(row.get("height", Double.class));
                    dto.setWidth(row.get("width", Double.class));
                    dto.setLength(row.get("length", Double.class));
                    dto.setThickness(row.get("thickness", Double.class));
                    dto.setFragile(row.get("is_fragile", Boolean.class));
                    dto.setIsPerishable(row.get("is_perishable", Boolean.class));
                    dto.setPhotoPacket(row.get("photo_url", String.class));
                    return dto;
                })
                .one()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .onErrorResume(e -> {
                    log.warn("Packet {} lookup failed — response served without the full packet",
                            packetId, e);
                    return Mono.just(Optional.empty());
                });
    }

    private DeliveryNeedResponseDTO mapToResponse(DeliveryNeed need) {
        return DeliveryNeedResponseDTO.builder()
                .id(need.getId())
                .userId(need.getUserId())
                .packetId(need.getPacketId())
                .pickupAddressId(need.getPickupAddressId())
                .deliveryAddressId(need.getDeliveryAddressId())
                .title(need.getTitle())
                .description(need.getDescription())
                .status(need.getStatus())
                .duration(need.getDuration())
                .signatureUrl(need.getSignatureUrl())
                .paymentMethod(need.getPaymentMethod())
                .transportMethod(need.getTransportMethod())
                .distance(need.getDistance())
                .deliveryId(need.getDeliveryId())
                .assignedFreelancerId(need.getAssignedFreelancerId())
                .createdAt(need.getCreatedAt())
                .updatedAt(need.getUpdatedAt())
                .pickupDeadline(need.getPickupDeadline())
                .build();
    }
}
