package com.yowyob.tiibntick.core.notify.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationPreferencesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationProvidersUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationRemindersUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IManageNotificationTemplatesUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISearchNotificationsUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.ISendNotificationUseCase;
import com.yowyob.tiibntick.core.notify.application.port.in.IViewKernelDeliveriesUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every route of {@link NotificationController} that names a recipient in its path (the feed, the
 * pending feed and the five preference routes) only serves the caller's own user/actor id; support
 * agents and platform administrators are the explicit exceptions. This drives the real controller,
 * so it fails if one route stops calling {@link RecipientAccessGuard}. Only the transport, the
 * identity resolver and the use cases are faked.
 */
class NotificationControllerOwnershipTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID ME = UUID.randomUUID();
    private static final UUID MY_ACTOR = UUID.randomUUID();
    private static final UUID SOMEONE_ELSE = UUID.randomUUID();

    private ISearchNotificationsUseCase search;
    private IManageNotificationPreferencesUseCase preferences;

    @BeforeEach
    void setUp() {
        search = mock(ISearchNotificationsUseCase.class);
        preferences = mock(IManageNotificationPreferencesUseCase.class);
        when(search.findByRecipientId(anyString(), any(), anyString())).thenReturn(Flux.empty());
        when(search.findPendingByRecipient(anyString(), any(), anyString())).thenReturn(Flux.empty());
        when(preferences.getPreferences(anyString(), any(), anyString())).thenReturn(Mono.empty());
        when(preferences.savePreferences(any())).thenReturn(Mono.empty());
        when(preferences.disableChannel(anyString(), any(), anyString(), any())).thenReturn(Mono.empty());
        when(preferences.enableChannel(anyString(), any(), anyString(), any())).thenReturn(Mono.empty());
        when(preferences.changeLanguage(anyString(), any(), anyString(), anyString())).thenReturn(Mono.empty());
    }

    private WebTestClient as(String... authorities) {
        var auth = new TestingAuthenticationToken("u", "n/a",
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        auth.setAuthenticated(true);
        NotificationController controller = new NotificationController(mock(ISendNotificationUseCase.class), search,
                preferences, mock(IManageNotificationProvidersUseCase.class),
                mock(IManageNotificationTemplatesUseCase.class), mock(IManageNotificationRemindersUseCase.class),
                mock(IViewKernelDeliveriesUseCase.class));
        return WebTestClient.bindToController(controller)
                .controllerAdvice(new ForbiddenAdvice())
                .argumentResolvers(r -> r.addCustomResolver(new IdentityResolver()))
                .webFilter((ex, chain) -> chain.filter(ex)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .build();
    }

    /** One call per route that carries a recipient id in its path. */
    private static List<WebTestClient.RequestHeadersSpec<?>> everyRecipientRoute(WebTestClient client, String id) {
        String base = "/api/v1/notifications";
        return List.of(
                client.get().uri(base + "/recipient/" + id),
                client.get().uri(base + "/recipient/" + id + "/pending"),
                client.get().uri(base + "/preferences/" + id),
                client.put().uri(base + "/preferences/" + id).bodyValue(
                        "{\"activeChannels\":[\"SMS_LOCAL\"],\"preferredLanguage\":\"fr\",\"notificationsEnabled\":true}"),
                client.post().uri(base + "/preferences/" + id + "/disable/SMS_LOCAL"),
                client.post().uri(base + "/preferences/" + id + "/enable/SMS_LOCAL"),
                client.patch().uri(base + "/preferences/" + id + "/language?localeTag=en"));
    }

    @Test
    @DisplayName("a user is refused another user's feed and preferences on all seven routes, nothing is read or written")
    void anotherUsersNotificationsAreRefused() {
        for (var call : everyRecipientRoute(as("ROLE_CLIENT"), SOMEONE_ELSE.toString())) {
            call.header("Content-Type", "application/json").exchange().expectStatus().isForbidden();
        }
        verifyNoInteractions(search, preferences);
    }

    @Test
    @DisplayName("a user reads and edits their own feed and preferences, by user id or by actor id")
    void ownNotificationsAreServed() {
        for (UUID own : List.of(ME, MY_ACTOR)) {
            for (var call : everyRecipientRoute(as("ROLE_FREELANCER"), own.toString())) {
                call.header("Content-Type", "application/json").exchange().expectStatus().isOk();
            }
        }
        verify(search).findByRecipientId(eq(TENANT.toString()), any(), eq(ME.toString()));
        verify(search).findByRecipientId(eq(TENANT.toString()), any(), eq(MY_ACTOR.toString()));
        verify(search, never()).findByRecipientId(anyString(), any(), eq(SOMEONE_ELSE.toString()));
    }

    @Test
    @DisplayName("support agents and platform administrators may read anyone's feed")
    void staffReadsAnyone() {
        as("ROLE_SUPPORT_AGENT").get().uri("/api/v1/notifications/recipient/" + SOMEONE_ELSE)
                .exchange().expectStatus().isOk();
        as("ROLE_TNT_ADMIN").get().uri("/api/v1/notifications/recipient/" + SOMEONE_ELSE + "/pending")
                .exchange().expectStatus().isOk();
        verify(search).findByRecipientId(eq(TENANT.toString()), any(), eq(SOMEONE_ELSE.toString()));
        verify(search).findPendingByRecipient(eq(TENANT.toString()), any(), eq(SOMEONE_ELSE.toString()));
    }

    /** In the running app Spring Security's exception translation turns the refusal into 403. */
    @RestControllerAdvice
    static class ForbiddenAdvice {
        @ExceptionHandler(AccessDeniedException.class)
        @ResponseStatus(HttpStatus.FORBIDDEN)
        Mono<Void> forbidden() {
            return Mono.empty();
        }
    }

    private static final class IdentityResolver implements HandlerMethodArgumentResolver {
        @Override public boolean supportsParameter(MethodParameter p) {
            return p.hasParameterAnnotation(CurrentUser.class);
        }

        @Override public Mono<Object> resolveArgument(MethodParameter p, BindingContext c, ServerWebExchange e) {
            return Mono.just(new TntUserIdentity(ME, TENANT, MY_ACTOR, null, null, Set.of(), false));
        }
    }
}
