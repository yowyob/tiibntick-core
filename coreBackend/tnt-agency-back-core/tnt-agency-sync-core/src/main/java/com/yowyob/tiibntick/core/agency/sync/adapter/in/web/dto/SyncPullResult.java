package com.yowyob.tiibntick.core.agency.sync.adapter.in.web.dto;

import java.util.List;
import java.util.Map;

public record SyncPullResult(String nextSyncToken, List<Map<String, Object>> changes) {}
