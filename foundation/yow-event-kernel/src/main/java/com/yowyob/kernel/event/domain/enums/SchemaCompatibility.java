package com.yowyob.kernel.event.domain.enums;

/**
 * Avro schema compatibility level enforced by the schema registry.
 *
 * <p>Mirrors the compatibility levels defined by the Confluent Schema Registry
 * specification, allowing downstream consumers to make informed decisions about
 * schema evolution.
 */
public enum SchemaCompatibility {

    /**
     * No compatibility checks performed. Any schema change is allowed.
     * <strong>Use with caution in production environments.</strong>
     */
    NONE,

    /**
     * New schema is backward compatible: consumers using the new schema can
     * read data written by the old schema.
     */
    BACKWARD,

    /**
     * New schema is forward compatible: consumers using the old schema can
     * read data written by the new schema.
     */
    FORWARD,

    /**
     * New schema is both backward and forward compatible.
     * This is the recommended level for production event contracts.
     */
    FULL,

    /**
     * All previous versions are backward compatible with the new schema.
     */
    BACKWARD_TRANSITIVE,

    /**
     * All previous versions are forward compatible with the new schema.
     */
    FORWARD_TRANSITIVE,

    /**
     * All previous versions are fully compatible with the new schema.
     */
    FULL_TRANSITIVE
}
