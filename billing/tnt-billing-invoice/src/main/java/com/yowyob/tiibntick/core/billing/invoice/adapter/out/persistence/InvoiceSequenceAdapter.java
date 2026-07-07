package com.yowyob.tiibntick.core.billing.invoice.adapter.out.persistence;

import com.yowyob.tiibntick.core.billing.invoice.application.port.out.InvoiceSequencePort;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter: generates atomic invoice sequence numbers using PostgreSQL sequences.
 * One sequence per tenant-year: "inv_seq_{tenantId}_{year}"
 *
 * @author MANFOUO Braun
 */
@Component
public class InvoiceSequenceAdapter implements InvoiceSequencePort {

    private final DatabaseClient databaseClient;

    public InvoiceSequenceAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Long> nextSequence(UUID tenantId, int year) {
        String seqKey = buildKey(tenantId, year);
        return databaseClient.sql(
                        "INSERT INTO tnt_invoice_sequences (seq_key, current_value) VALUES (:key, 1) " +
                                "ON CONFLICT (seq_key) DO UPDATE SET current_value = tnt_invoice_sequences.current_value + 1 " +
                                "RETURNING current_value")
                .bind("key", seqKey)
                .fetch()
                .one()
                .map(row -> ((Number) row.get("current_value")).longValue());
    }

    private String buildKey(UUID tenantId, int year) {
        return "inv_" + tenantId.toString().replace("-", "") + "_" + year;
    }
}
