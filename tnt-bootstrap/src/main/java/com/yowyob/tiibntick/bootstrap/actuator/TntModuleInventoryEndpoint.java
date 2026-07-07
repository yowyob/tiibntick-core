package com.yowyob.tiibntick.bootstrap.actuator;

import com.yowyob.tiibntick.bootstrap.config.TntModuleRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Custom Spring Boot Actuator endpoint exposing the TiiBnTick module inventory.
 * <p>
 * Accessible at:
 * <ul>
 *   <li>{@code GET /actuator/tnt-modules} — full module report</li>
 *   <li>{@code GET /actuator/tnt-modules/{moduleId}} — single module descriptor</li>
 * </ul>
 *
 * @author MANFOUO Braun
 */
@Component
@Endpoint(id = "tnt-modules")
@RequiredArgsConstructor
public class TntModuleInventoryEndpoint {

    private final TntModuleRegistry registry;

    /**
     * Returns the full module report with all 32 module descriptors and statistics.
     *
     * @return {@link TntModuleRegistry.ModuleReport}
     */
    @ReadOperation
    public Mono<TntModuleRegistry.ModuleReport> read() {
        return Mono.fromCallable(registry::generateReport);
    }

    /**
     * Returns the descriptor for a single module by its ID.
     *
     * @param moduleId the module artifact ID (e.g., "tnt-media-core")
     * @return module descriptor, or error if not found
     */
    @ReadOperation
    public Mono<TntModuleRegistry.ModuleDescriptor> readModule(@Selector String moduleId) {
        return Mono.justOrEmpty(registry.getAll().get(moduleId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Module not found: " + moduleId)));
    }

    /**
     * Returns all modules in a given layer.
     *
     * @param layer the layer name (e.g., "LOGISTICS_L3")
     * @return list of module descriptors
     */
    public Mono<List<TntModuleRegistry.ModuleDescriptor>> readByLayer(String layer) {
        return Mono.fromCallable(() -> {
            TntModuleRegistry.ModuleLayer moduleLayer =
                    TntModuleRegistry.ModuleLayer.valueOf(layer.toUpperCase());
            return registry.getByLayer(moduleLayer);
        });
    }
}
