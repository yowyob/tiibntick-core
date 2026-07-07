package com.yowyob.tiibntick.bootstrap.health;

import com.yowyob.tiibntick.bootstrap.bridge.KernelConnectionStatus;
import com.yowyob.tiibntick.bootstrap.config.TntModuleRegistry;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Aggregated global health status of TiiBnTick Core.
 * Populated by {@link TntHealthConfig} and exposed via {@code /actuator/health}.
 *
 * @author MANFOUO Braun
 */
@Getter
@Builder
public final class TntGlobalHealthStatus {

    private final HealthStatus overallStatus;
    private final KernelConnectionStatus kernelConnection;
    private final DatabasePyramidStatus databasePyramid;
    private final Map<String, TntModuleRegistry.ModuleStatus> modulesStatus;
    private final boolean kafkaStatus;
    private final boolean redisStatus;
    private final boolean orToolsLoaded;
    private final LocalDateTime checkedAt;

    public boolean isFullyOperational() {
        return overallStatus == HealthStatus.UP
                && (kernelConnection == null || kernelConnection.isFullyOperational())
                && (databasePyramid == null || databasePyramid.allConnected())
                && kafkaStatus && redisStatus;
    }

    public List<String> degradedComponents() {
        List<String> degraded = new ArrayList<>();
        if (kernelConnection != null && !kernelConnection.isFullyOperational()) {
            degraded.add("kernel");
        }
        if (!kafkaStatus) degraded.add("kafka");
        if (!redisStatus) degraded.add("redis");
        if (!orToolsLoaded) degraded.add("or-tools-jni");
        if (databasePyramid != null) {
            degraded.addAll(databasePyramid.failedDatabases());
        }
        return degraded;
    }

    public List<String> criticalFailures() {
        List<String> critical = new ArrayList<>();
        if (databasePyramid != null && !databasePyramid.isTntCoreDbConnected()) {
            critical.add("tnt-core-database");
        }
        if (!kafkaStatus) critical.add("kafka");
        return critical;
    }
}
