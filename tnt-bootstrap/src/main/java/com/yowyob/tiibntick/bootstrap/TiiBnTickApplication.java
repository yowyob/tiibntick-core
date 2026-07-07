package com.yowyob.tiibntick.bootstrap;

import com.google.ortools.Loader;
import com.yowyob.tiibntick.bootstrap.bridge.KernelBridgeConfig;
import com.yowyob.tiibntick.bootstrap.config.TntCoreConfig;
import com.yowyob.tiibntick.bootstrap.config.TiiBnTickApplicationContext;
import com.yowyob.tiibntick.bootstrap.startup.StartupStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * TiiBnTick Core — ⭐ Single executable entry point.
 * <p>
 * This is the ONLY class annotated with {@code @SpringBootApplication} in the entire
 * TiiBnTick Core project. All other modules ({@code tnt-****-core}) are non-runnable
 * library JARs assembled here.
 * <p>
 * Startup sequence:
 * <ol>
 *   <li>JVM static initializer: load OR-Tools JNI native libs (must precede Spring context)</li>
 *   <li>Spring Boot context initialization → all module auto-configurations activated</li>
 *   <li>{@link KernelBridgeConfig} wires the Yowyob Kernel WebClient</li>
 *   <li>{@link TntCoreConfig} orchestrates all module configs</li>
 *   <li>Liquibase migrations run for all module schemas</li>
 *   <li>Kafka consumers/producers and WebSocket STOMP broker start</li>
 *   <li>{@link com.yowyob.tiibntick.bootstrap.startup.TntStartupRunner} executes 9 startup steps</li>
 *   <li>Application declared READY — health probes become healthy</li>
 * </ol>
 *
 * @author MANFOUO Braun
 */

/* 
@ComponentScan(
        basePackages = {
                "com.yowyob.tiibntick",
                "yowyob.comops.api"
        },
        excludeFilters = {
            @Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                    yowyob.comops.api.kernel.application.service.AuditAspect.class,
                    yowyob.comops.api.product.application.service.ProductApplicationService.class
                }
            )
        }
)
*/
@Slf4j
@SpringBootApplication
@ComponentScan(
    basePackages = {
        "com.yowyob.tiibntick" //,
        //"yowyob.comops.api.kernel.client",      // Clients HTTP vers le Kernel
        //"yowyob.comops.api.kernel.config",
        //"yowyob.comops.api.common.util",        // Utilitaires partagés
        //"yowyob.comops.api.common.config"       // Configs partagées
    } //,
    /*excludeFilters = {
            @Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = com.yowyob.tiibntick.core.actor.adapter.out.auth.ActorCoreYowAuthTntAdapter.class
            )
    }*/
)
@Import({
        KernelBridgeConfig.class,
        TntCoreConfig.class
})
//@EnableR2dbcRepositories(basePackages = {
        //"com.yowyob.tiibntick.bootstrap",
        //"com.yowyob.tiibntick.core"
//})
@RequiredArgsConstructor
public class TiiBnTickApplication {

    static {
        // Load Google OR-Tools native JNI libraries.
        // MUST execute before the Spring context starts — OR-Tools uses a static loader.
        // Requires Debian-based Docker image (glibc / not Alpine musl libc).
        try {
            Loader.loadNativeLibraries();
            log.info("OR-Tools 9.8.3296 native libraries loaded successfully");
        } catch (Exception e) {
            // Non-fatal in dev. Critical in prod — logged and the OR-Tools health
            // indicator will report DEGRADED.
            log.warn("OR-Tools native libraries not loaded: {} " +
                     "— VRP/CVRP features degraded (use Debian Docker image in production)",
                    e.getMessage());
        }
    }

    private final TiiBnTickApplicationContext appContext;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TiiBnTickApplication.class);
        app.run(args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        String port = event.getApplicationContext().getEnvironment()
                .getProperty("server.port", "8080");
        String hostAddress;
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            hostAddress = "localhost";
        }

        String statusIcon = appContext.getStartupStatus() == StartupStatus.COMPLETED ? "✅"
                : appContext.getStartupStatus() == StartupStatus.DEGRADED ? "⚠️" : "❌";

        log.info("""
                
                ╔══════════════════════════════════════════════════════════════╗
                ║          TiiBnTick Core v0.0.1 — Application Ready           ║
                ╠══════════════════════════════════════════════════════════════╣
                ║  Status:     {} {}                                           ║
                ║  Modules:    {} modules active                               ║
                ║  Local:      http://localhost:{}                             ║
                ║  External:   http://{}:{}                                    ║
                ║  Swagger:    http://localhost:{}/swagger-ui.html             ║
                ║  Health:     http://localhost:{}/actuator/health             ║
                ║  Modules:    http://localhost:{}/actuator/tnt-modules        ║
                ║  Kernel:     http://localhost:{}/actuator/tnt-kernel         ║
                ╚══════════════════════════════════════════════════════════════╝
                """,
                statusIcon,
                appContext.getStartupStatus().name(),
                appContext.getModuleCount(),
                port,
                hostAddress, port,
                port,
                port,
                port,
                port);
    }
}
