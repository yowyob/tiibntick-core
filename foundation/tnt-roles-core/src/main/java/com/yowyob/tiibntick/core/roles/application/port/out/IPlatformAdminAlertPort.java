package com.yowyob.tiibntick.core.roles.application.port.out;

import com.yowyob.tiibntick.core.roles.domain.model.PlatformRoleGrant;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Outbound port — tells the world that a platform administrator was granted or revoked (decision D-172): audit
 * event, e-mail to the beneficiary and to the system-tenant owner. Every implementation is best-effort: an alert
 * that fails never undoes the grant or the revocation, the registry row being the durable record.
 *
 * @author MANFOUO Braun
 */
public interface IPlatformAdminAlertPort {

    /** {@code systemOwnerUserId} may be null when the system-tenant owner has not logged in yet. */
    Mono<Void> granted(PlatformRoleGrant grant, UUID systemOwnerUserId);

    Mono<Void> revoked(PlatformRoleGrant grant, UUID systemOwnerUserId);
}
