package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.bootstrap.health.DatabasePyramidStatus;
import com.yowyob.tiibntick.bootstrap.bridge.KernelConnectionStatus;
import com.yowyob.tiibntick.bootstrap.startup.StartupStatus;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Runtime application context VO for TiiBnTick Core.
 * Aggregates startup time, active profile, assembled modules, kernel connectivity
 * and database pyramid status. Exposed via {@code /actuator/info} and
 * {@code /actuator/tnt/modules}.
 *
 * @author MANFOUO Braun
 */
@Getter
@Setter
@Component
public class TiiBnTickApplicationContext {

    public static final String SOLUTION_CODE = "TNT";

    private LocalDateTime startupTime;
    private ApplicationProfile activeProfile;
    private List<TntModuleRegistry.ModuleDescriptor> resolvedModules;
    private KernelConnectionStatus kernelConnectionStatus;
    private DatabasePyramidStatus databasePyramidStatus;
    private StartupStatus startupStatus = StartupStatus.INITIALIZING;

    public boolean isReady() {
        return StartupStatus.COMPLETED == startupStatus || StartupStatus.DEGRADED == startupStatus;
    }

    public int getModuleCount() {
        return resolvedModules != null ? resolvedModules.size() : 0;
    }

    public String getKernelVersion() {
        return kernelConnectionStatus != null ? kernelConnectionStatus.getKernelVersion() : "unknown";
    }

    public String getSolutionCode() {
        return SOLUTION_CODE;
    }
}
