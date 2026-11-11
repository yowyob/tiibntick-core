package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatHub.ChatEvent;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatService.ChatMessage;
import com.yowyob.tiibntick.core.gofreelancer.application.service.NegotiationChatService.ChatThread;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Negotiation conversation of an announcement under {@code /api/announcements/{id}/chat}.
 * The client names the delivery person with {@code deliveryPersonId}; a delivery person always gets
 * their own conversation. Every endpoint checks participation in the service.
 */
@RestController
@RequestMapping("/api/announcements/{announcementId}/chat")
@RequiredArgsConstructor
public class NegotiationChatController {

    /** Shape the frontend renders: author identity and display name next to the text. */
    public record MessageResponse(UUID id, UUID announcementId, UUID senderId, String content, Instant createdAt,
                                  Instant readAt, Sender sender) {}

    public record Sender(UUID id, String firstName, String lastName, String userType) {}

    /** Only the text is read from the body: the author and role come from the token. */
    public record PostMessageRequest(String content) {}

    public record ThreadResponse(UUID deliveryPersonId, MessageResponse lastMessage, long unread) {}

    public record ReadResponse(long marked) {}

    /** Live event: {@code type} is message | read | typing. */
    public record EventResponse(String type, MessageResponse message, UUID actorId, Instant readAt) {}

    static final Duration HEARTBEAT = Duration.ofSeconds(25);

    private final NegotiationChatService chat;

    private static UUID caller(TntSecurityContext ctx) {
        if (ctx == null || ctx.userId() == null) {
            throw new AccessDeniedException("Authentication required");
        }
        return ctx.userId();
    }

    static MessageResponse toResponse(ChatMessage m) {
        if (m == null) {
            return null;
        }
        return new MessageResponse(m.id(), m.announcementId(), m.senderId(), m.content(), m.createdAt(), m.readAt(),
                new Sender(m.senderId(), m.senderFirstName() != null ? m.senderFirstName() : "",
                        m.senderLastName() != null ? m.senderLastName() : "",
                        NegotiationChatService.FREELANCER.equals(m.senderRole()) ? "LIVREUR" : "CLIENT"));
    }

    @GetMapping
    public Flux<MessageResponse> list(@PathVariable UUID announcementId,
                                      @RequestParam(required = false) UUID deliveryPersonId,
                                      @CurrentUser(required = false) TntSecurityContext ctx) {
        return Flux.defer(() -> chat.list(announcementId, caller(ctx), deliveryPersonId))
                .map(NegotiationChatController::toResponse);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<MessageResponse> post(@PathVariable UUID announcementId,
                                      @RequestParam(required = false) UUID deliveryPersonId,
                                      @RequestBody PostMessageRequest request,
                                      @CurrentUser(required = false) TntSecurityContext ctx) {
        return Mono.defer(() -> chat.post(announcementId, caller(ctx), deliveryPersonId, request.content()))
                .map(NegotiationChatController::toResponse);
    }

    /** The caller has seen the other party's messages: stored and pushed to the conversation as a read receipt. */
    @PostMapping("/read")
    public Mono<ReadResponse> read(@PathVariable UUID announcementId,
                                   @RequestParam(required = false) UUID deliveryPersonId,
                                   @CurrentUser(required = false) TntSecurityContext ctx) {
        return Mono.defer(() -> chat.markRead(announcementId, caller(ctx), deliveryPersonId)).map(ReadResponse::new);
    }

    /** Ephemeral "is typing" signal. */
    @PostMapping("/typing")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> typing(@PathVariable UUID announcementId,
                             @RequestParam(required = false) UUID deliveryPersonId,
                             @CurrentUser(required = false) TntSecurityContext ctx) {
        return Mono.defer(() -> chat.typing(announcementId, caller(ctx), deliveryPersonId));
    }

    /** Conversations of the caller on this announcement, with the last message and the unread count. */
    @GetMapping("/threads")
    public Flux<ThreadResponse> threads(@PathVariable UUID announcementId,
                                        @CurrentUser(required = false) TntSecurityContext ctx) {
        return Flux.defer(() -> chat.threads(announcementId, caller(ctx)))
                .map((ChatThread t) -> new ThreadResponse(t.deliveryPersonId(), toResponse(t.lastMessage()), t.unread()));
    }

    /**
     * Live events of the conversation. The access check runs before the stream opens, so a stranger
     * gets a 403 instead of a silent stream; a comment every 25 s keeps proxies from closing it.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Mono<ResponseEntity<Flux<ServerSentEvent<EventResponse>>>> stream(
            @PathVariable UUID announcementId,
            @RequestParam(required = false) UUID deliveryPersonId,
            @CurrentUser(required = false) TntSecurityContext ctx) {
        return Mono.defer(() -> chat.openStream(announcementId, caller(ctx), deliveryPersonId))
                .map(live -> {
                    Flux<ServerSentEvent<EventResponse>> events = live
                            .map((ChatEvent e) -> ServerSentEvent.<EventResponse>builder(
                                            new EventResponse(e.type(), toResponse(e.message()), e.actorId(), e.readAt()))
                                    .event(e.type()).build());
                    Flux<ServerSentEvent<EventResponse>> heartbeat = Flux.interval(HEARTBEAT)
                            .map(i -> ServerSentEvent.<EventResponse>builder().comment("keep-alive").build());
                    return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM)
                            .body(Flux.merge(Flux.just(ServerSentEvent.<EventResponse>builder().comment("connected").build()),
                                    events, heartbeat));
                });
    }
}
