package com.yowyob.tiibntick.bootstrap.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Enables AspectJ auto-proxying for the TiiBnTick Core application.
 *
 * <p>This configuration is required for the {@code @RequirePermission} AOP aspect defined in
 * {@code tnt-roles-core} ({@code TntPermissionAspect}) to intercept reactive service methods.
 *
 * <p>Key settings:
 * <ul>
 *   <li>{@code proxyTargetClass = true} — forces CGLIB subclass proxies instead of JDK dynamic
 *       proxies. Required for Spring WebFlux services that do not implement interfaces.</li>
 *   <li>AOP enforcement can be disabled globally via {@code tnt.roles.aop-enabled=false}
 *       (e.g. in test profiles where {@code @MockBean} replaces the permission evaluator).</li>
 * </ul>
 *
 * <p><strong>Note:</strong> {@code spring.aop.auto} must NOT be set to {@code false}
 * in {@code application.yml} or this configuration will have no effect.
 *
 * @author MANFOUO Braun
 * @see com.yowyob.tiibntick.core.roles.adapter.in.web.TntPermissionAspect
 * @see com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission
 */
@Slf4j
@Configuration
@EnableAspectJAutoProxy(proxyTargetClass = true)
public class TntAopConfiguration {

    @Value("${tnt.roles.aop-enabled:true}")
    private boolean aopEnabled;

    /**
     * Logs the AOP status at context initialization time so operators can
     * verify whether permission enforcement is active in the current profile.
     */
    public void logAopStatus() {
        if (aopEnabled) {
            log.info("TiiBnTick permission AOP enforcement is ENABLED — " +
                     "@RequirePermission annotations are active on all service methods");
        } else {
            log.warn("TiiBnTick permission AOP enforcement is DISABLED " +
                     "(tnt.roles.aop-enabled=false) — suitable for test profiles only");
        }
    }
}
