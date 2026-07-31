package com.yowyob.tiibntick.core.gofreelancer.application.port.out;

import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port for an external negotiation chat system (product BFF / messaging).
 *
 * <p>GOFP does not own chat lifecycle — callers may open a thread when a QUOTE_REQUEST
 * announcement enters negotiation. Default adapter is a no-op stub.
 *
 * @author MANFOUO BRAUN
 */
public interface INegotiationChatPort {

    /**
     * Opens (or reuses) a negotiation chat thread for the given announcement.
     *
     * @param announcementId delivery-core announcement id
     * @param clientId       client actor id
     * @param freelancerId   freelancer / delivery-person id
     * @return chat thread identifier (opaque to GOFP), or empty if unsupported
     */
    Mono<String> openNegotiationThread(UUID announcementId, UUID clientId, UUID freelancerId);
}
