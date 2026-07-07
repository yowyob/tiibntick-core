package com.yowyob.tiibntick.core.tp.adapter.out.persistence;

import com.yowyob.tiibntick.core.tp.application.port.out.PhoneAliasPort;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Adapter: implements PhoneAliasPort.
 * Generates short, unique phone aliases stored in the tnt_phone_aliases table.
 *
 * <p>Alias format: +237-6XX-XXX-XXX (Cameroon MTN/Orange prefix pool reserved for TiiBnTick).
 * The alias maps to the real phone securely in the database (accessed only by relay hub operators).</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class PhoneAliasAdapter implements PhoneAliasPort {

    private final R2dbcEntityTemplate template;

    public PhoneAliasAdapter(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Mono<String> generateAlias(UUID tenantId, UUID thirdPartyId) {
        String alias = buildAlias(thirdPartyId);
        return template.getDatabaseClient()
                .sql("INSERT INTO tnt_phone_aliases (id, tenant_id, third_party_id, alias, created_at) " +
                        "VALUES (gen_random_uuid(), :tenantId, :thirdPartyId, :alias, NOW()) " +
                        "ON CONFLICT (tenant_id, third_party_id) DO UPDATE SET alias = EXCLUDED.alias, created_at = NOW()")
                .bind("tenantId", tenantId)
                .bind("thirdPartyId", thirdPartyId)
                .bind("alias", alias)
                .fetch()
                .rowsUpdated()
                .thenReturn(alias);
    }

    @Override
    public Mono<String> resolveAlias(String alias) {
        return template.getDatabaseClient()
                .sql("SELECT third_party_id::text FROM tnt_phone_aliases WHERE alias = :alias")
                .bind("alias", alias)
                .fetch()
                .one()
                .map(row -> (String) row.get("third_party_id"))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Alias not found: " + alias)));
    }

    @Override
    public Mono<Void> revokeAlias(UUID tenantId, UUID thirdPartyId) {
        return template.getDatabaseClient()
                .sql("DELETE FROM tnt_phone_aliases WHERE tenant_id = :tenantId AND third_party_id = :thirdPartyId")
                .bind("tenantId", tenantId)
                .bind("thirdPartyId", thirdPartyId)
                .fetch()
                .rowsUpdated()
                .then();
    }

    /**
     * Generates a deterministic alias from the UUID (last 9 hex digits → 9 digits mod 10^9).
     * Format: +237-6TT-NNN-NNN (prefix 6 reserved for TiiBnTick internal routing).
     */
    private String buildAlias(UUID thirdPartyId) {
        long seed = Math.abs(thirdPartyId.getLeastSignificantBits() % 1_000_000_000L);
        return String.format("+237-6TT-%03d-%03d", (seed / 1000) % 1000, seed % 1000);
    }
}
