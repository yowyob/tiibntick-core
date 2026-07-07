package com.yowyob.tiibntick.bootstrap.actuator;

import com.yowyob.tiibntick.bootstrap.bridge.YowyobKernelBridge;
import com.yowyob.tiibntick.bootstrap.config.SolutionContext;
import com.yowyob.tiibntick.bootstrap.config.TntModuleRegistry;
import com.yowyob.tiibntick.bootstrap.deployment.DockerImageDescriptor;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contributes comprehensive TiiBnTick metadata to {@code /actuator/info}.
 * <p>
 * Sections contributed:
 * <ul>
 *   <li>{@code tiibntick.solution} — solution identity and profile</li>
 *   <li>{@code tiibntick.modules} — module registry report</li>
 *   <li>{@code tiibntick.kernel} — Yowyob Kernel connectivity status</li>
 *   <li>{@code tiibntick.docker} — Docker image requirements</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
@RequiredArgsConstructor
public class TntInfoContributor implements InfoContributor {

    private final TntModuleRegistry moduleRegistry;
    private final SolutionContext solutionContext;
    private final YowyobKernelBridge kernelBridge;

    @Override
    public void contribute(Info.Builder builder) {
        builder.withDetail("tiibntick", buildTntSection());
    }

    private Map<String, Object> buildTntSection() {
        Map<String, Object> tnt = new LinkedHashMap<>();

        // ── Solution identity ──────────────────────────────────────────────────
        tnt.put("solution", Map.of(
                "code", solutionContext.getSolutionCode(),
                "name", solutionContext.getSolutionName(),
                "version", solutionContext.getVersion(),
                "activeProfile", solutionContext.getActiveProfile() != null
                        ? solutionContext.getActiveProfile().name() : "DEV",
                "supportedLocales", solutionContext.getSupportedLocales(),
                "supportedCurrencies", solutionContext.getSupportedCurrencies(),
                "generatedAt", LocalDateTime.now().toString()
        ));

        // ── Module report ──────────────────────────────────────────────────────
        TntModuleRegistry.ModuleReport report = moduleRegistry.generateReport();
        tnt.put("modules", Map.of(
                "total", report.totalModules(),
                "tntExclusive", report.tntExclusiveModules(),
                "tntExtensions", report.tntExtensionModules(),
                "kernelContributions", report.kernelContributions(),
                "withSchemas", report.modulesWithSchemas(),
                "totalKafkaTopics", report.totalKafkaTopics(),
                "summary", report.summary(),
                "byLayer", moduleRegistry.getAll().values().stream()
                        .collect(Collectors.groupingBy(
                                m -> m.layer().name(),
                                Collectors.counting()))
        ));

        // ── Kernel status ──────────────────────────────────────────────────────
        var kernelStatus = kernelBridge.getConnectionStatus();
        tnt.put("kernel", Map.of(
                "connected", kernelStatus.isConnected(),
                "version", kernelStatus.getKernelVersion() != null ? kernelStatus.getKernelVersion() : "unknown",
                "yowAuthReachable", kernelStatus.isYowAuthReachable(),
                "eventBusReachable", kernelStatus.isEventBusReachable(),
                "fullyOperational", kernelStatus.isFullyOperational()
        ));

        // ── Docker descriptor ──────────────────────────────────────────────────
        DockerImageDescriptor docker = DockerImageDescriptor.production();
        List<String> missing = docker.validate();
        tnt.put("docker", Map.of(
                "baseImage", docker.getBaseImage(),
                "baseOs", docker.getBaseOs(),
                "exposedPorts", docker.getExposedPorts(),
                "requiredEnvVars", DockerImageDescriptor.REQUIRED_ENV_VARS,
                "missingEnvVars", missing,
                "configurationValid", missing.isEmpty()
        ));

        return tnt;
    }
}
