package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import reactor.core.publisher.Mono;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-tests the two callbacks on {@link TntEntityIsNewCallback}:
 * <ol>
 *   <li>After a DB read ({@code AfterConvertCallback}): {@code isNew} must be {@code false}.</li>
 *   <li>After a DB write ({@code AfterSaveCallback}): {@code isNew} must also be {@code false},
 *       so that a second {@code save()} on the same in-memory entity issues an UPDATE,
 *       not a duplicate INSERT (Bug #2 regression guard).</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */
class TntEntityIsNewCallbackTest {

    private final TntEntityIsNewCallback callback = new TntEntityIsNewCallback();
    private final SqlIdentifier table = SqlIdentifier.unquoted("test_table");

    @Test
    void afterConvert_setsIsNewToFalse() {
        StubEntity entity = new StubEntity();
        assertThat(entity.isNew()).isTrue();

        Mono.from(callback.onAfterConvert(entity, table)).block();

        assertThat(entity.isNew()).isFalse();
    }

    @Test
    void afterSave_setsIsNewToFalse() {
        StubEntity entity = new StubEntity();
        assertThat(entity.isNew()).isTrue();

        Mono.from(callback.onAfterSave(entity, new OutboundRow(), table)).block();

        assertThat(entity.isNew()).isFalse();
    }

    @Test
    void afterSave_entityCanBeReadBackWithIsNewFalse_independentlyOfAfterConvert() {
        // Simulates the double-save pattern: create → save1 → save2
        // After save1, isNew must be false so save2 issues UPDATE, not INSERT.
        StubEntity entity = new StubEntity();
        assertThat(entity.isNew()).as("fresh entity is new").isTrue();

        // First save
        Mono.from(callback.onAfterSave(entity, new OutboundRow(), table)).block();
        assertThat(entity.isNew()).as("after first save, isNew=false").isFalse();

        // Second save — should still be false (UPDATE, not INSERT)
        Mono.from(callback.onAfterSave(entity, new OutboundRow(), table)).block();
        assertThat(entity.isNew()).as("after second save, isNew still false").isFalse();
    }

    // ── Minimal stub ────────────────────────────────────────────────────────

    static class StubEntity implements TntPersistableEntity {
        private boolean isNew = true;
        private final UUID id = UUID.randomUUID();

        @Override
        public void markNotNew() {
            this.isNew = false;
        }

        public boolean isNew() {
            return isNew;
        }

        public UUID getId() {
            return id;
        }
    }
}
