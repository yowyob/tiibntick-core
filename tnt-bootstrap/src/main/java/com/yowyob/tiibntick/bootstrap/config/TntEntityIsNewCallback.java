package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.common.persistence.TntPersistableEntity;
import org.reactivestreams.Publisher;
import org.springframework.data.r2dbc.mapping.OutboundRow;
import org.springframework.data.r2dbc.mapping.event.AfterConvertCallback;
import org.springframework.data.r2dbc.mapping.event.AfterSaveCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Flips {@link TntPersistableEntity#markNotNew()} in two cases:
 * <ul>
 *   <li>After a DB read ({@link AfterConvertCallback}) — so that a subsequent
 *       {@code save()} on the returned instance issues an {@code UPDATE}.</li>
 *   <li>After a DB write ({@link AfterSaveCallback}) — so that a second
 *       {@code save()} on the same in-memory entity (e.g. after OTP hash
 *       population) also issues an {@code UPDATE} rather than a duplicate
 *       {@code INSERT}.</li>
 * </ul>
 * Shared across every module in this monolith — see {@link TntPersistableEntity}
 * for why this is needed.
 *
 * @author MANFOUO Braun
 */
@Component
public class TntEntityIsNewCallback
        implements AfterConvertCallback<TntPersistableEntity>,
                   AfterSaveCallback<TntPersistableEntity> {

    @Override
    public Publisher<TntPersistableEntity> onAfterConvert(TntPersistableEntity entity, SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }

    @Override
    public Publisher<TntPersistableEntity> onAfterSave(TntPersistableEntity entity, OutboundRow row, SqlIdentifier table) {
        entity.markNotNew();
        return Mono.just(entity);
    }
}
