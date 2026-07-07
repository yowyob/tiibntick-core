package com.yowyob.kernel.event.application.port.in;

import com.yowyob.kernel.event.domain.enums.SchemaCompatibility;

/**
 * Command record for registering a new Avro schema version.
 *
 * @param eventType      fully qualified event type name
 * @param solutionCode   Yowyob solution that owns this schema
 * @param avroSchemaJson the Avro schema definition in JSON format
 * @param compatibility  compatibility level to enforce against prior versions
 */
public record RegisterSchemaCommand(
        String eventType,
        String solutionCode,
        String avroSchemaJson,
        SchemaCompatibility compatibility
) {}
