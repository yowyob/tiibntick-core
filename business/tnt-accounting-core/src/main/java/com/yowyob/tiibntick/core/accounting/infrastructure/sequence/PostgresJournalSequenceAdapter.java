package com.yowyob.tiibntick.core.accounting.infrastructure.sequence;

import com.yowyob.tiibntick.core.accounting.application.port.out.JournalSequencePort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Implements JournalSequencePort using a PostgreSQL sequence per tenant per year.
 * Sequences are created on demand as accounting.journal_seq_{tenantShort}_{year}.
 * Author: MANFOUO Braun
 */
@Component
public class PostgresJournalSequenceAdapter implements JournalSequencePort {

    private final DatabaseClient databaseClient;

    public PostgresJournalSequenceAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Long> nextSequence(UUID tenantId, int year) {
        String seqName = buildSequenceName(tenantId, year);
        return ensureSequenceExists(seqName)
                .then(databaseClient.sql("SELECT nextval('accounting." + seqName + "')")
                        .map(row -> row.get(0, Long.class))
                        .one());
    }

    private Mono<Void> ensureSequenceExists(String seqName) {
        String ddl = String.format(
                "CREATE SEQUENCE IF NOT EXISTS accounting.%s START 1 INCREMENT 1 NO CYCLE", seqName);
        return databaseClient.sql(ddl).then();
    }

    private String buildSequenceName(UUID tenantId, int year) {
        // Short deterministic name: jnl_seq_{first8charsOfTenantId}_{year}
        String tenantShort = tenantId.toString().replace("-", "").substring(0, 8);
        return "jnl_seq_" + tenantShort + "_" + year;
    }
}
