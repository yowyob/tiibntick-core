package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.reactivestreams.Publisher;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Flips {@link TntPersistableEntity#markNotNew()} once an entity has actually been read
 * back from the database, so a subsequent {@code save()} on that instance correctly
 * issues an {@code UPDATE} — while entities freshly built in application code (never
 * read from the DB) stay "new" and correctly issue an {@code INSERT}. Shared across
 * every module in this monolith — see {@link TntPersistableEntity} for why this is
 * needed.
 *
 * @author MANFOUO Braun
 */
@Component
public class TntEntityIsNewCallback implements AfterConvertCallback<TntPersistableEntity> {

    @Override
    public Publisher<TntPersistableEntity> onAfterConvert(TntPersistableEntity entity, SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }
}
