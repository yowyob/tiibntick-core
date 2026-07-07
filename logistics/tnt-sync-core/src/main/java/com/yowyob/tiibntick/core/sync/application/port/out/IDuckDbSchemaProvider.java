package com.yowyob.tiibntick.core.sync.application.port.out;

import reactor.core.publisher.Mono;

public interface IDuckDbSchemaProvider {
    /**
     * Returns the DDL SQL for initializing the client-side DuckDB-Wasm local database.
     * This DDL is served to PWA/mobile clients on first sync.
     *
     * @param tenantId the tenant context (some tables may be tenant-specific)
     * @return Mono with the DDL SQL string
     */
    Mono<String> getDdl(String tenantId);
}
