package com.yowyob.tiibntick.core.sales.adapter.out.persistence.adapter;

import com.yowyob.tiibntick.core.sales.application.port.out.OrderNumberGeneratorPort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Generates unique, monotonic TiiBnTick order numbers using PostgreSQL sequences.
 * Format: TNT-ORD-{agencyShort}-{year}-{seq:06d}
 * Example: TNT-ORD-YDE-2026-000042
 * Author: MANFOUO Braun
 */
@Component
public class PostgresOrderNumberGeneratorAdapter implements OrderNumberGeneratorPort {

    private final DatabaseClient databaseClient;

    public PostgresOrderNumberGeneratorAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<String> generate(UUID tenantId, UUID agencyId, int year) {
        String seqName = buildSequenceName(agencyId, year);
        return ensureSequenceExists(seqName)
                .then(databaseClient.sql("SELECT nextval('sales." + seqName + "')")
                        .map(row -> row.get(0, Long.class))
                        .one())
                .map(seq -> {
                    String agencyCode = agencyId.toString().replace("-", "").substring(0, 3).toUpperCase();
                    return String.format("TNT-ORD-%s-%d-%06d", agencyCode, year, seq);
                });
    }

    private Mono<Void> ensureSequenceExists(String seqName) {
        return databaseClient.sql(
                "CREATE SEQUENCE IF NOT EXISTS sales." + seqName + " START 1 INCREMENT 1 NO CYCLE")
                .then();
    }

    private String buildSequenceName(UUID agencyId, int year) {
        String agencyShort = agencyId.toString().replace("-", "").substring(0, 8);
        return "ord_seq_" + agencyShort + "_" + year;
    }
}
