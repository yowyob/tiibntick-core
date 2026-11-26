package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpUserProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Lot C-56 — the caller's own name, kept on its gofp user row (TiiBnTick side, nothing is written to
 * the Kernel). Shown to the other party: a courier's name in the sender's candidate list, the
 * sender's name pre-filled on the next shipment. Resolved from the token: no id in the request.
 */
@RestController
@RequestMapping("/api/v1/gofp/users/me")
@RequiredArgsConstructor
public class GofpUserMeController {

    static final int MAX_NAME = 80;

    private final GofpUserRepository users;
    private final GofpUserProvisioningService provisioning;

    public record MyName(String firstName, String lastName) { }

    @GetMapping
    public Mono<MyName> me(@CurrentUser(required = false) TntSecurityContext ctx) {
        if (ctx == null || ctx.userId() == null) {
            return Mono.error(new AccessDeniedException("An authenticated user is required"));
        }
        return users.findByCoreUserId(ctx.userId())
                .map(u -> new MyName(blankToNull(u.getFirstName()), blankToNull(u.getLastName())))
                .defaultIfEmpty(new MyName(null, null));
    }

    @PutMapping
    public Mono<MyName> rename(@RequestBody(required = false) MyName body,
                               @CurrentUser(required = false) TntSecurityContext ctx) {
        if (ctx == null || ctx.userId() == null) {
            return Mono.error(new AccessDeniedException("An authenticated user is required"));
        }
        String first = body == null ? null : clean(body.firstName());
        String last = body == null ? null : clean(body.lastName());
        if (first == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "firstName is required (1 to " + MAX_NAME + " characters)"));
        }
        return provisioning.provisionIfAbsent(ctx.userId())
                .flatMap(u -> {
                    u.setFirstName(first);
                    u.setLastName(last == null ? "" : last);
                    u.setUpdatedAt(Instant.now());
                    u.markNotNew(); // the row exists (found or just provisioned): UPDATE, never INSERT
                    return users.save(u);
                })
                .map(u -> new MyName(blankToNull(u.getFirstName()), blankToNull(u.getLastName())));
    }

    static String clean(String raw) {
        if (raw == null) return null;
        String v = raw.strip().replaceAll("\\s+", " ");
        if (v.isEmpty() || v.length() > MAX_NAME) return null;
        return v;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
