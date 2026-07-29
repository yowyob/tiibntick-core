package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsDTO {
    private long pendingCount;
    private long activeCount;
    private long suspendedCount;
    private long rejectedCount;
    private long revokedCount;
}
