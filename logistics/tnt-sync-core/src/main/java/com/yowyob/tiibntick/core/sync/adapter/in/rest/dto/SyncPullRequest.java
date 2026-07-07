package com.yowyob.tiibntick.core.sync.adapter.in.rest.dto;

import java.util.Set;

public record SyncPullRequest(
        String syncToken,
        Set<String> filterAggregates,
        Integer maxRecords
) {}
