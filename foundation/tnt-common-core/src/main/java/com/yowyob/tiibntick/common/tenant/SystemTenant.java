package com.yowyob.tiibntick.common.tenant;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The ONE system tenant of TiiBnTick — the tenant that holds the global role definitions and
 * is used by background / Kafka flows that have no HTTP security context.
 *
 * <p>There is deliberately NO default and no literal anywhere in the code base: the value comes
 * from the deployment and the application refuses to start without it. Every module (roles,
 * Go-Freelancer, accounting, incidents…) obtains it from here. It is resolved once, in this order:
 * <ol>
 *   <li>JVM system property {@code tnt.roles.system-tenant-id} (used by the Maven test runs);</li>
 *   <li>environment variable {@code TNT_SYSTEM_TENANT_ID} (the same variable
 *       {@code application.yml} maps to {@code tnt.roles.system-tenant-id}).</li>
 * </ol>
 * It is read from the environment rather than from Spring because several services hold it in
 * {@code static final} fields initialised at class-load time; the bootstrap module fails the
 * start-up when the Spring property disagrees with this value (see
 * {@code SystemTenantConsistencyCheck}), so there can never be two system tenants.
 *
 * @author MANFOUO Braun
 */
public final class SystemTenant {

    public static final String SYSTEM_PROPERTY = "tnt.roles.system-tenant-id";
    public static final String ENV_VARIABLE = "TNT_SYSTEM_TENANT_ID";

    private static final class Holder {
        static final UUID RESOLVED = resolve(System::getProperty, System::getenv);
    }

    private SystemTenant() {
    }

    /** The system tenant of this JVM; fails with an explicit message when it is not configured. */
    public static UUID id() {
        return Holder.RESOLVED;
    }

    /**
     * Identity used as the "author" of automatic actions (SLA monitors, schedulers…), derived
     * deterministically from the system tenant so that no second literal has to be configured.
     */
    public static UUID systemActorId() {
        return UUID.nameUUIDFromBytes(("tnt-system-actor|" + id()).getBytes(StandardCharsets.UTF_8));
    }

    /** Resolution rule, exposed for tests. */
    public static UUID resolve(UnaryOperator<String> systemProperty, UnaryOperator<String> environment) {
        String raw = firstNonBlank(systemProperty.apply(SYSTEM_PROPERTY), environment.apply(ENV_VARIABLE));
        if (raw == null) {
            throw new IllegalStateException("The TiiBnTick system tenant is not configured: set the environment "
                    + "variable " + ENV_VARIABLE + " to the UUID of the system tenant (there is no default).");
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid system tenant id '" + raw + "' (" + ENV_VARIABLE + " / "
                    + SYSTEM_PROPERTY + " must be a UUID)", ex);
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }
}
