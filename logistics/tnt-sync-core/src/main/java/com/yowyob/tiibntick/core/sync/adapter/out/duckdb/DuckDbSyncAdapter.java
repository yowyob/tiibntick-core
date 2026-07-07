package com.yowyob.tiibntick.core.sync.adapter.out.duckdb;

import com.yowyob.tiibntick.core.sync.application.port.out.IDuckDbSchemaProvider;
import org.duckdb.DuckDBConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DuckDB JDBC adapter with two responsibilities:
 *
 * 1. SCHEMA PROVISION: Provides the DDL SQL that PWA/mobile clients use to initialize
 *    their local DuckDB-Wasm database. This DDL is served via GET /api/v1/sync/schema/duckdb.
 *
 * 2. SERVER-SIDE ANALYTICS (optional): Can be used for fast analytical queries on
 *    sync data without hitting PostgreSQL (e.g. sync latency statistics, conflict rate analysis).
 *
 * Author: MANFOUO Braun
 */
@Component
public class DuckDbSyncAdapter implements IDuckDbSchemaProvider {

    private static final Logger log = LoggerFactory.getLogger(DuckDbSyncAdapter.class);

    /**
     * DuckDB-Wasm DDL for the client-side local database.
     * This schema matches the entity_version table structure on the server.
     * Clients execute this DDL once to initialize their offline store.
     */
    private static final String CLIENT_DDL = """
            -- TiiBnTick Client-Side DuckDB-Wasm Schema
            -- Version: 1.0  |  Author: MANFOUO Braun
            -- Execute once during app initialization (DuckDB-Wasm OPFS backend)
            
            CREATE TABLE IF NOT EXISTS tnt_entity_version (
                aggregate_type   VARCHAR NOT NULL,
                aggregate_id     VARCHAR NOT NULL,
                operation        VARCHAR NOT NULL,  -- CREATED | UPDATED | DELETED | STATUS_CHANGED
                payload_json     TEXT,
                server_timestamp TIMESTAMP NOT NULL,
                server_version   BIGINT NOT NULL,
                synced_at        TIMESTAMP DEFAULT current_timestamp,
                PRIMARY KEY (aggregate_type, aggregate_id)
            );
            
            CREATE TABLE IF NOT EXISTS tnt_offline_queue (
                id               VARCHAR PRIMARY KEY,
                type             VARCHAR NOT NULL,  -- OfflineOpType enum
                aggregate_type   VARCHAR NOT NULL,
                aggregate_id     VARCHAR NOT NULL,
                payload          TEXT NOT NULL,
                local_timestamp  TIMESTAMP NOT NULL,
                sequence_number  BIGINT NOT NULL,
                status           VARCHAR DEFAULT 'QUEUED',
                retry_count      INTEGER DEFAULT 0,
                created_at       TIMESTAMP DEFAULT current_timestamp
            );
            
            CREATE TABLE IF NOT EXISTS tnt_sync_metadata (
                key              VARCHAR PRIMARY KEY,
                value            TEXT NOT NULL,
                updated_at       TIMESTAMP DEFAULT current_timestamp
            );
            
            -- Index for fast delta queries
            CREATE INDEX IF NOT EXISTS idx_entity_version_type
                ON tnt_entity_version (aggregate_type, server_timestamp);
            
            CREATE INDEX IF NOT EXISTS idx_offline_queue_status
                ON tnt_offline_queue (status, sequence_number);
            
            -- Seed sync metadata
            INSERT OR IGNORE INTO tnt_sync_metadata (key, value)
                VALUES ('sync_token', ''), ('last_sync_at', '1970-01-01T00:00:00');
            """;

    @Override
    public Mono<String> getDdl(String tenantId) {
        return Mono.just(CLIENT_DDL)
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Creates an in-memory DuckDB connection for server-side analytics.
     * Used in tests and for analytical queries on sync metadata.
     *
     * @return a DuckDB connection (caller must close it)
     * @throws SQLException if the connection cannot be created
     */
    public DuckDBConnection openInMemory() throws SQLException {
        try {
            Class.forName("org.duckdb.DuckDBDriver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("DuckDB driver not found", e);
        }
        return (DuckDBConnection) DriverManager.getConnection("jdbc:duckdb:");
    }

    /**
     * Initializes the schema in an in-memory DuckDB instance and loads entity version records.
     * Used for fast analytical queries (e.g. conflict rate per aggregate type).
     *
     * @param records the entity version records to load
     * @return row count loaded
     */
    public Mono<Integer> loadForAnalytics(java.util.List<com.yowyob.tiibntick.core.sync.domain.model.EntityVersionRecord> records) {
        return Mono.fromCallable(() -> {
            try (var conn = openInMemory();
                 var stmt = conn.createStatement()) {

                stmt.execute(CLIENT_DDL);

                var pstmt = conn.prepareStatement(
                        "INSERT INTO tnt_entity_version VALUES (?,?,?,?,?,?,?)");

                int count = 0;
                for (var rec : records) {
                    pstmt.setString(1, rec.aggregateType());
                    pstmt.setString(2, rec.aggregateId());
                    pstmt.setString(3, rec.operation().name());
                    pstmt.setString(4, rec.payloadJson());
                    pstmt.setObject(5, rec.updatedAt());
                    pstmt.setLong(6, rec.version());
                    pstmt.setObject(7, rec.updatedAt());
                    pstmt.addBatch();
                    count++;
                }
                pstmt.executeBatch();
                pstmt.close();
                log.debug("Loaded {} records into DuckDB analytics instance", count);
                return count;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
