package com.yowyob.tiibntick.core.sync.domain.service;

import com.yowyob.tiibntick.core.sync.domain.model.ConflictRecord;
import com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord;
import com.yowyob.tiibntick.core.sync.domain.model.OfflineOperation;
import com.yowyob.tiibntick.core.sync.domain.model.VectorClock;
import com.yowyob.tiibntick.core.sync.domain.model.enums.ConflictResolution;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;

public class ConflictResolverService {

    private static final Logger log = LoggerFactory.getLogger(ConflictResolverService.class);

    public enum Strategy {
        LWW,
        SERVER_ALWAYS_WINS,
        CLIENT_ALWAYS_WINS,
        VECTOR_CLOCK
    }

    private final Strategy defaultStrategy;

    public ConflictResolverService(Strategy defaultStrategy) {
        this.defaultStrategy = defaultStrategy;
    }

    public ConflictResolverService() {
        this(Strategy.LWW);
    }

    public ConflictRecord resolve(OfflineOperation clientOp, EntityVersionRecord serverVersion) {
        return resolve(clientOp, serverVersion, defaultStrategy);
    }

    public ConflictRecord resolve(OfflineOperation clientOp, EntityVersionRecord serverVersion, Strategy strategy) {
        log.debug("Resolving conflict for {}/{} using strategy {}",
                clientOp.getAggregateType(), clientOp.getAggregateId(), strategy);

        return switch (strategy) {
            case LWW -> resolveLww(clientOp, serverVersion);
            case SERVER_ALWAYS_WINS -> buildRecord(clientOp, serverVersion, ConflictResolution.SERVER_WINS, serverVersion.payloadJson());
            case CLIENT_ALWAYS_WINS -> buildRecord(clientOp, serverVersion, ConflictResolution.CLIENT_WINS, clientOp.getPayload());
            case VECTOR_CLOCK -> resolveVectorClock(clientOp, serverVersion);
        };
    }

    /**
     * Last Write Wins (LWW): the operation with the most recent timestamp prevails.
     * Client wins only when its timestamp is strictly later than the server's.
     */
    private ConflictRecord resolveLww(OfflineOperation clientOp, EntityVersionRecord serverVersion) {
        LocalDateTime clientTs = clientOp.getLocalTimestamp();
        LocalDateTime serverTs = serverVersion.updatedAt();

        boolean clientIsNewer = clientTs.isAfter(serverTs);
        ConflictResolution resolution = clientIsNewer ? ConflictResolution.CLIENT_WINS : ConflictResolution.SERVER_WINS;
        String resolvedValue = clientIsNewer ? clientOp.getPayload() : serverVersion.payloadJson();

        log.debug("LWW: clientTs={}, serverTs={} → {}", clientTs, serverTs, resolution);

        return buildRecord(clientOp, serverVersion, resolution, resolvedValue);
    }

    /**
     * Vector Clock resolution: uses causal relationships to determine winner.
     * Falls back to LWW if vector clocks indicate concurrent (no causal order).
     */
    private ConflictRecord resolveVectorClock(OfflineOperation clientOp, EntityVersionRecord serverVersion) {
        VectorClock clientClock = extractVectorClock(clientOp.getPayload());
        VectorClock serverClock = extractVectorClock(serverVersion.payloadJson());

        VectorClock.CausalRelation relation = clientClock.compareWith(serverClock);

        return switch (relation) {
            case HAPPENS_BEFORE -> buildRecord(clientOp, serverVersion, ConflictResolution.CLIENT_WINS, clientOp.getPayload());
            case HAPPENS_AFTER -> buildRecord(clientOp, serverVersion, ConflictResolution.SERVER_WINS, serverVersion.payloadJson());
            case CONCURRENT, CONCURRENT_EQUAL -> {
                // Concurrent modifications: flag for manual merge or fall back to LWW
                log.info("Concurrent modifications on {}/{} — falling back to LWW",
                        clientOp.getAggregateType(), clientOp.getAggregateId());
                yield resolveLww(clientOp, serverVersion);
            }
        };
    }

    private ConflictRecord buildRecord(OfflineOperation clientOp, EntityVersionRecord serverVersion,
                                       ConflictResolution resolution, String resolvedValue) {
        return new ConflictRecord(
                clientOp.getAggregateType(),
                clientOp.getAggregateId(),
                clientOp.getPayload(),
                serverVersion.payloadJson(),
                clientOp.getLocalTimestamp(),
                serverVersion.updatedAt(),
                resolution,
                resolvedValue
        );
    }

    /**
     * Extracts vector clock metadata from a JSON payload.
     * Expects a "__vc" field at the top level.
     * Falls back to empty vector clock if not present.
     */
    private VectorClock extractVectorClock(String json) {
        if (json == null || json.isBlank() || !json.contains("\"__vc\"")) {
            return VectorClock.empty();
        }
        try {
            int start = json.indexOf("\"__vc\"");
            int braceStart = json.indexOf('{', start + 6);
            int braceEnd = json.indexOf('}', braceStart);
            String vcJson = json.substring(braceStart + 1, braceEnd);
            Map<String, Long> clocks = new java.util.HashMap<>();
            for (String entry : vcJson.split(",")) {
                String[] parts = entry.replace("\"", "").split(":");
                if (parts.length == 2) {
                    clocks.put(parts[0].trim(), Long.parseLong(parts[1].trim()));
                }
            }
            return VectorClock.of(clocks);
        } catch (Exception e) {
            log.debug("Failed to extract vector clock from payload: {}", e.getMessage());
            return VectorClock.empty();
        }
    }
}
