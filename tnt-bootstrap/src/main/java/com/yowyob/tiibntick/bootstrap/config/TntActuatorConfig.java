package com.yowyob.tiibntick.bootstrap.config;

import com.yowyob.tiibntick.bootstrap.actuator.TntInfoContributor;
import com.yowyob.tiibntick.bootstrap.actuator.TntKernelStatusEndpoint;
import com.yowyob.tiibntick.bootstrap.actuator.TntModuleInventoryEndpoint;
import com.yowyob.tiibntick.bootstrap.deployment.DockerImageDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration that wires the custom {@code /actuator/info} contributor.
 * <p>
 * {@link TntModuleInventoryEndpoint} ({@code /actuator/tnt-modules}) and
 * {@link TntKernelStatusEndpoint} ({@code /actuator/tnt-kernel}) are {@code @Component}
 * beans, auto-detected via component scan — they must not also be declared here as
 * {@code @Bean} factory methods, or Spring registers two endpoint beans for the same id.
 * <p>
 * The {@code /actuator/info} endpoint is enriched by {@link TntInfoContributor}
 * with TiiBnTick-specific metadata (modules, solution context, Docker descriptor).
 *
 * @author MANFOUO Braun
 */
@Slf4j
@Configuration
public class TntActuatorConfig {

    @Bean
    public InfoContributor dockerInfoContributor() {
        DockerImageDescriptor descriptor = DockerImageDescriptor.production();
        return builder -> builder.withDetail("docker", java.util.Map.of(
                "baseImage", descriptor.getBaseImage(),
                "baseOs", descriptor.getBaseOs(),
                "exposedPorts", descriptor.getExposedPorts(),
                "healthCheckPath", descriptor.getHealthCheckPath(),
                "requiredEnvVars", DockerImageDescriptor.REQUIRED_ENV_VARS,
                "missingEnvVars", descriptor.validate()
        ));
    }
}
