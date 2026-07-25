package com.yowyob.tiibntick.core.platformgateway.adapter.out.persistence.entity;

/**
 * Marks an R2DBC entity whose {@code @Id} is assigned client-side (via
 * {@code UUID.randomUUID()}) rather than DB-generated. Spring Data R2DBC's default
 * {@code isNew()} check treats a non-null {@code @Id} as "already exists" and issues an
 * {@code UPDATE} instead of an {@code INSERT} — since these entities always have a
 * non-null ID by the time {@code save()} is called, that {@code UPDATE} silently affects
 * zero rows (R2DBC, unlike Spring Data JDBC, does not throw on a zero-row update).
 * Implementing {@code Persistable} plus this marker lets {@link TntEntityIsNewCallback}
 * flip new-ness back to {@code false} only for entities actually read back from the DB.
 *
 * @author MANFOUO Braun
 */
public interface TntPersistableEntity {

    void markNotNew();
}
