package com.yowyob.tiibntick.core.administration.application.port.in;

import com.yowyob.tiibntick.core.administration.domain.model.TntPermissionEntry;
import reactor.core.publisher.Flux;

/**
 * Lists all TiiBnTick-specific permissions, optionally filtered by module.
 *
 * @author MANFOUO Braun
 */
public interface ListTntPermissionsUseCase {
    Flux<TntPermissionEntry> listTntPermissions();

    Flux<TntPermissionEntry> listByModule(String module);
}
