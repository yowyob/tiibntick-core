package com.yowyob.tiibntick.core.sync.adapter.in.rest;

import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.DuckDbSchemaResponse;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPullResponse;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushRequest;
import com.yowyob.tiibntick.core.sync.adapter.in.rest.dto.SyncPushResponse;
import com.yowyob.tiibntick.core.sync.application.port.in.IComputeDeltaUseCase;
import com.yowyob.tiibntick.core.sync.application.port.in.IProcessSyncBatchUseCase;
import com.yowyob.tiibntick.core.sync.application.port.out.IDuckDbSchemaProvider;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private static final Logger log = LoggerFactory.getLogger(SyncController.class);

    private final IProcessSyncBatchUseCase processSyncBatch;
    private final IComputeDeltaUseCase computeDelta;
    private final IDuckDbSchemaProvider schemaProvider;

    public SyncController(IProcessSyncBatchUseCase processSyncBatch,
                          IComputeDeltaUseCase computeDelta,
                          IDuckDbSchemaProvider schemaProvider) {
        this.processSyncBatch = processSyncBatch;
        this.computeDelta = computeDelta;
        this.schemaProvider = schemaProvider;
    }

    /**
     * Push-sync: client submits operations queued while offline.
     * Server processes each op, resolves conflicts, and returns the new sync token.
     *
     * POST /api/v1/sync/push
     * Authorization: Bearer {jwt}
     * X-User-Id, X-Tenant-Id, X-Device-Id from security context / headers
     */
    @PostMapping(value = "/push", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed(value = "tnt.sync.push.duration", description = "Duration of push-sync operations")
    public Mono<ResponseEntity<SyncPushResponse>> pushSync(
            @RequestHeader(value = "X-User-Id") String userId,
            @RequestHeader(value = "X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Device-Id", required = false, defaultValue = "unknown") String deviceId,
            @RequestBody SyncPushRequest request) {

        log.debug("Push sync request from user={}, tenant={}, ops={}", userId, tenantId,
                request.hasOperations() ? request.operations().size() : 0);

        return processSyncBatch.processSyncBatch(userId, tenantId, deviceId, request)
                .map(ResponseEntity::ok)
                .doOnError(ex -> log.error("Push sync failed for user={}: {}", userId, ex.getMessage()));
    }

    /**
     * Pull-sync (delta pull): server returns all changes since the client's last sync token.
     *
     * GET /api/v1/sync/pull?syncToken={token}&filter=MISSION,PACKAGE
     */
    @GetMapping(value = "/pull", produces = MediaType.APPLICATION_JSON_VALUE)
    @Timed(value = "tnt.sync.pull.duration", description = "Duration of delta pull operations")
    public Mono<ResponseEntity<SyncPullResponse>> pullSync(
            @RequestHeader(value = "X-User-Id") String userId,
            @RequestHeader(value = "X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Device-Id", required = false, defaultValue = "unknown") String deviceId,
            @RequestParam(value = "syncToken", required = false) String syncToken,
            @RequestParam(value = "filter", required = false) Set<String> filterAggregates) {

        log.debug("Pull sync request from user={}, tenant={}, token={}", userId, tenantId, syncToken);

        return computeDelta.computeDelta(userId, tenantId, deviceId, syncToken, filterAggregates)
                .map(ResponseEntity::ok)
                .doOnError(ex -> log.error("Pull sync failed for user={}: {}", userId, ex.getMessage()));
    }

    /**
     * Full bootstrap delta: returns all current state for a device doing its first sync.
     *
     * GET /api/v1/sync/bootstrap?filter=MISSION,PACKAGE
     */
    @GetMapping(value = "/bootstrap", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<SyncPullResponse>> bootstrap(
            @RequestHeader(value = "X-User-Id") String userId,
            @RequestHeader(value = "X-Tenant-Id") String tenantId,
            @RequestHeader(value = "X-Device-Id", required = false, defaultValue = "unknown") String deviceId,
            @RequestParam(value = "filter", required = false) Set<String> filterAggregates) {

        log.info("Bootstrap sync for user={}, tenant={}", userId, tenantId);
        return computeDelta.computeDelta(userId, tenantId, deviceId, null, filterAggregates)
                .map(ResponseEntity::ok);
    }

    /**
     * Returns the DuckDB-Wasm DDL schema for the client-side local database.
     * Called once by the PWA or React Native app on first install.
     *
     * GET /api/v1/sync/schema/duckdb
     */
    @GetMapping(value = "/schema/duckdb", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<DuckDbSchemaResponse>> getDuckDbSchema(
            @RequestHeader(value = "X-Tenant-Id", defaultValue = "default") String tenantId) {

        return schemaProvider.getDdl(tenantId)
                .map(ddl -> ResponseEntity.ok(new DuckDbSchemaResponse(ddl, "1.0", "TiiBnTick DuckDB-Wasm schema")));
    }
}
