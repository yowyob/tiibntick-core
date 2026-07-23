package com.yowyob.tiibntick.core.roles.application.port.out;

import java.util.UUID;

/**
 * Outbound port — broadcasts RBAC permission changes so every application instance can
 * invalidate its local permission cache instead of waiting out the TTL.
 *
 * @author MANFOUO Braun
 */
public interface IPermissionChangeNotifier {

    /** Notifies that a single user's effective permissions changed within a tenant. */
    void notifyChanged(UUID tenantId, UUID userId);

    /** Notifies that permissions changed tenant-wide (e.g. a role definition itself changed). */
    void notifyTenantChanged(UUID tenantId);
}
