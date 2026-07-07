package com.yowyob.tiibntick.core.sync.adapter.in.rest.dto;

public record DuckDbSchemaResponse(
        String ddl,
        String version,
        String description
) {}
