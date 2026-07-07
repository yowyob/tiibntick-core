package com.yowyob.tiibntick.core.auth.adapter.in.web;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for injecting the current authenticated user's identity into
 * WebFlux controller method parameters.
 *
 * <p>Supports the following parameter types:
 * <ul>
 *   <li>{@link com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity} — lightweight projection</li>
 *   <li>{@link com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext} — full context</li>
 * </ul>
 *
 * <p>Usage example in a reactive controller:
 * <pre>{@code
 * @GetMapping("/missions")
 * public Flux<MissionView> listMyMissions(@CurrentUser TntUserIdentity currentUser) {
 *     return missionService.findByActor(currentUser.actorId(), currentUser.tenantId());
 * }
 * }</pre>
 *
 * @author MANFOUO Braun
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
    /**
     * If true, the endpoint requires an authenticated user.
     * When false and no authenticated context exists, an anonymous value is injected.
     * Defaults to true.
     */
    boolean required() default true;
}
