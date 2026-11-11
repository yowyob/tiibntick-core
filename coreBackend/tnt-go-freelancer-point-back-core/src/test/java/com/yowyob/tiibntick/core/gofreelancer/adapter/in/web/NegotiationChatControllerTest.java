package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatHub.ChatEvent;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatService.ChatMessage;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatService.ChatThread;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.HandlerMethodArgumentResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The chat API through a real WebFlux dispatcher: JSON shapes, caller binding and the SSE wire format. */
@DisplayName("Negotiation chat REST + SSE")
class NegotiationChatControllerTest {

    private final UUID announcement = UUID.randomUUID();
    private final UUID caller = UUID.randomUUID();
    private final UUID person = UUID.randomUUID();
    private final NegotiationChatService service = mock(NegotiationChatService.class);

    /** Resolves {@code @CurrentUser TntSecurityContext} from a header so a test can be anonymous. */
    private WebTestClient client() {
        HandlerMethodArgumentResolver resolver = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter p) {
                return p.hasParameterAnnotation(CurrentUser.class) && TntSecurityContext.class.equals(p.getParameterType());
            }
            @Override public Mono<Object> resolveArgument(MethodParameter p, BindingContext b, ServerWebExchange ex) {
                String id = ex.getRequest().getHeaders().getFirst("X-Test-User");
                return id == null ? Mono.just(TntSecurityContext.anonymous())
                        : Mono.just(TntSecurityContext.builder().userId(UUID.fromString(id)).authenticated(true).build());
            }
        };
        return WebTestClient.bindToController(new NegotiationChatController(service))
                .argumentResolvers(c -> c.addCustomResolver(resolver))
                .controllerAdvice(new Denied())
                .build();
    }

    /** Same mapping as the application's security layer: refused participant -> 403. */
    @org.springframework.web.bind.annotation.RestControllerAdvice
    public static class Denied {
        @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
        public org.springframework.http.ResponseEntity<Void> denied() {
            return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
    }

    private ChatMessage message(String text, String role, Instant readAt) {
        return new ChatMessage(UUID.randomUUID(), announcement, caller, role, "Jo", "Driver", text,
                Instant.parse("2030-01-01T10:00:00Z"), readAt);
    }

    @Test
    @DisplayName("GET lists the thread with the sender shaped for the screen")
    void list() {
        when(service.list(announcement, caller, person)).thenReturn(Flux.just(message("Bonjour", "FREELANCER", null)));
        client().get().uri("/api/announcements/{a}/chat?deliveryPersonId={p}", announcement, person)
                .header("X-Test-User", caller.toString()).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].content").isEqualTo("Bonjour")
                .jsonPath("$[0].sender.userType").isEqualTo("LIVREUR")
                .jsonPath("$[0].sender.firstName").isEqualTo("Jo")
                .jsonPath("$[0].readAt").doesNotExist();
    }

    @Test
    @DisplayName("POST stores the text only: the author is the caller whatever the body claims")
    void postUsesTheCaller() {
        when(service.post(eq(announcement), eq(caller), eq(person), eq("Salut"))).thenReturn(Mono.just(message("Salut", "CLIENT", null)));
        client().post().uri("/api/announcements/{a}/chat?deliveryPersonId={p}", announcement, person)
                .header("X-Test-User", caller.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"content\":\"Salut\",\"senderId\":\"" + UUID.randomUUID() + "\",\"senderRole\":\"CLIENT\"}")
                .exchange().expectStatus().isCreated()
                .expectBody().jsonPath("$.sender.userType").isEqualTo("CLIENT");
        verify(service).post(announcement, caller, person, "Salut");
    }

    @Test
    @DisplayName("an anonymous caller and a refused participant both get 403")
    void forbidden() {
        client().get().uri("/api/announcements/{a}/chat", announcement).exchange().expectStatus().isForbidden();
        when(service.list(any(), any(), any())).thenReturn(Flux.error(new AccessDeniedException("no")));
        client().get().uri("/api/announcements/{a}/chat", announcement)
                .header("X-Test-User", caller.toString()).exchange().expectStatus().isForbidden();
        client().get().uri("/api/announcements/{a}/chat/stream", announcement).accept(MediaType.TEXT_EVENT_STREAM)
                .exchange().expectStatus().isForbidden();
        when(service.openStream(any(), any(), any())).thenReturn(Mono.error(new AccessDeniedException("no")));
        client().get().uri("/api/announcements/{a}/chat/stream", announcement).accept(MediaType.TEXT_EVENT_STREAM)
                .header("X-Test-User", caller.toString()).exchange().expectStatus().isForbidden();
    }

    @Test
    @DisplayName("read, typing and threads are wired to the service")
    void readTypingThreads() {
        when(service.markRead(announcement, caller, person)).thenReturn(Mono.just(2L));
        when(service.typing(announcement, caller, person)).thenReturn(Mono.empty());
        when(service.threads(announcement, caller)).thenReturn(Flux.just(
                new ChatThread(person, person, message("15h ?", "FREELANCER", null), 3)));

        client().post().uri("/api/announcements/{a}/chat/read?deliveryPersonId={p}", announcement, person)
                .header("X-Test-User", caller.toString()).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.marked").isEqualTo(2);
        client().post().uri("/api/announcements/{a}/chat/typing?deliveryPersonId={p}", announcement, person)
                .header("X-Test-User", caller.toString()).exchange().expectStatus().isNoContent();
        client().get().uri("/api/announcements/{a}/chat/threads", announcement)
                .header("X-Test-User", caller.toString()).exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$[0].unread").isEqualTo(3)
                .jsonPath("$[0].lastMessage.content").isEqualTo("15h ?");
    }

    @Test
    @DisplayName("the stream is text/event-stream, opens with a comment and carries named events")
    void stream() {
        when(service.openStream(announcement, caller, person)).thenReturn(Mono.just(Flux.just(
                ChatEvent.message(message("Coucou", "CLIENT", null)),
                ChatEvent.typing(person))));

        FluxExchangeResult<org.springframework.http.codec.ServerSentEvent<String>> result = client().get()
                .uri("/api/announcements/{a}/chat/stream?deliveryPersonId={p}", announcement, person)
                .header("X-Test-User", caller.toString())
                .accept(MediaType.TEXT_EVENT_STREAM).exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(new org.springframework.core.ParameterizedTypeReference<>() {});

        StepVerifier.create(result.getResponseBody()
                        .filter(e -> e.event() != null).take(2).collectList())
                .assertNext(events -> {
                    assertThat(events).extracting(org.springframework.http.codec.ServerSentEvent::event)
                            .containsExactly("message", "typing");
                    assertThat(events.get(0).data()).contains("\"content\":\"Coucou\"");
                    assertThat(events.get(1).data()).contains("\"type\":\"typing\"");
                })
                .verifyComplete();
    }
}
