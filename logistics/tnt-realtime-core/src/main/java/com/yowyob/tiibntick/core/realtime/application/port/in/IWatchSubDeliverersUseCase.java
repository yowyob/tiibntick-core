package com.yowyob.tiibntick.core.realtime.application.port.in;

import com.yowyob.tiibntick.core.realtime.domain.model.GPSStreamEntry;
import reactor.core.publisher.Flux;

/**
 * Inbound port: real-time tracking of sub-deliverers within a FreelancerOrganization.
 *
 * <p>Enables the FreelancerOrg OWNER to watch all their active sub-deliverers'
 * live GPS positions via WebSocket or SSE.
 *
 * <p>The OWNER subscribes to {@code /topic/fleet/{freelancerOrgId}} and receives
 * GPS pings from all sub-deliverers whose {@code GpsPingMessage.freelancerOrgId}
 * matches the org. This use case also provides the REST SSE endpoint for non-WebSocket
 * clients.
 *
 * <p><b>Kernel integration principle:</b> The {@code freelancerOrgId} is a plain
 * UUID String referencing tnt-organization-core — no class import across modules.
 *
 * @author MANFOUO Braun
 */
public interface IWatchSubDeliverersUseCase {

    /**
     * Returns a stream of live GPS positions for all sub-deliverers in the given FreelancerOrg.
     *
     * <p>This stream is a hot, infinite Flux backed by Redis Pub/Sub.
     * It emits a new value whenever any sub-deliverer sends a GPS ping tagged with
     * the given {@code freelancerOrgId}.
     *
     * <p>The caller is responsible for cancelling the subscription (e.g., when the WebSocket
     * session closes or the SSE connection is terminated).
     *
     * @param freelancerOrgId the FreelancerOrg UUID to watch (from tnt-organization-core)
     * @param tenantId        tenant scope
     * @return infinite reactive stream of GPS entries from sub-deliverers in this org
     */
    Flux<GPSStreamEntry> watchSubDeliverers(String freelancerOrgId, String tenantId);

    /**
     * Returns the last known GPS position for each active sub-deliverer in the org.
     *
     * <p>Used to populate the initial state of the FreelancerOrg OWNER's dashboard
     * before the live stream begins.
     *
     * @param freelancerOrgId the FreelancerOrg UUID
     * @param tenantId        tenant scope
     * @return Flux of the most recent GPS entry per sub-deliverer
     */
    Flux<GPSStreamEntry> getLastKnownPositions(String freelancerOrgId, String tenantId);
}
