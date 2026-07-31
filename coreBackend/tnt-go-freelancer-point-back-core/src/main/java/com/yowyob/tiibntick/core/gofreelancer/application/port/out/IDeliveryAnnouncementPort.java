package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.PublishAnnouncementPortCommand;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.RespondToAnnouncementPortCommand;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Outbound port owned by {@code tnt-go-freelancer-point-back-core} for the announcement
 * lifecycle it delegates to {@code tnt-delivery-core} (see {@code architecture/decisions.md}
 * ADR-021).
 *
 * <p>gofp's use cases ({@code MatchingUseCase}, {@code AnnouncementApplicationService}) depend
 * only on this interface and the gofp-owned DTOs in {@code application.port.out.dto} — never on
 * {@code tnt-delivery-core}'s {@code DeliveryAnnouncement} aggregate or its inbound ports
 * directly. {@link com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery.DeliveryAnnouncementPortAdapter}
 * is the single adapter that talks to {@code tnt-delivery-core} and performs the translation —
 * a change to that module's internal aggregate shape only ever requires touching the adapter.
 *
 * @author MANFOUO BRAUN
 */
public interface IDeliveryAnnouncementPort {

    /**
     * Publishes a new announcement. Equivalent to {@code DeliveryAnnouncementUseCase.publishAnnouncement}.
     */
    Mono<AnnouncementSnapshot> publish(PublishAnnouncementPortCommand command);

    /**
     * Cancels an announcement the client has not yet assigned.
     */
    Mono<Void> cancel(UUID tenantId, UUID announcementId, UUID clientId);

    /**
     * Registers a freelancer's response/bid on an announcement.
     */
    Mono<AnnouncementSnapshot> respond(RespondToAnnouncementPortCommand command);

    /**
     * Client selects one of the responses, triggering delivery creation in tnt-delivery-core.
     */
    Mono<AnnouncementSnapshot> selectResponse(UUID tenantId, UUID announcementId, UUID clientId, UUID responseId);

    /**
     * Resolves the escrow amount for a candidate response — {@code offeredAmount} for
     * FIXED_PRICE, the response's own {@code proposedPrice} for QUOTE_REQUEST. Delegates to
     * {@code DeliveryAnnouncement.resolveEscrowAmount} so the rule stays defined once, in
     * tnt-delivery-core, rather than being re-implemented here.
     *
     * @throws com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException
     *         if the response has no valid amount for the announcement's pricing mode —
     *         already mapped to HTTP 409 by gofp's {@code GlobalExceptionHandler}, so this
     *         is treated as an expected domain-error contract, not an internals leak.
     */
    Mono<BigDecimal> resolveEscrowAmount(UUID tenantId, UUID announcementId, UUID responseId);

    Flux<AnnouncementSnapshot> findOpenAnnouncements(UUID tenantId);

    Mono<AnnouncementSnapshot> findById(UUID tenantId, UUID announcementId);

    Flux<AnnouncementSnapshot> findByClient(UUID tenantId, UUID clientId);
}
