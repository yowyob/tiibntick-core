package com.yowyob.tiibntick.core.i18n.adapter.in.web;

import com.yowyob.kernel.i18n.application.service.MessageCatalog;
import com.yowyob.kernel.i18n.application.service.MessageCatalogAdminService;
import com.yowyob.kernel.i18n.domain.model.MessageEntry;
import com.yowyob.kernel.i18n.domain.model.MessageVersion;
import com.yowyob.kernel.i18n.domain.model.MissingTranslation;
import com.yowyob.tiibntick.core.auth.adapter.in.web.CurrentUser;
import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/** TNT_ADMIN-only management of the message catalog (no screen: used through the API / Swagger). */
@RestController
@RequestMapping("/api/v1/admin/i18n")
@PreAuthorize("hasRole('TNT_ADMIN')")
@Tag(name = "i18n admin", description = "Message catalog management — TNT_ADMIN only")
public class I18nAdminController {

    public record MessageView(String namespace, String key, String language, String variant, String text,
                              String draftText, int version, Instant updatedAt, String updatedBy) {
        static MessageView of(MessageEntry e) {
            return new MessageView(e.namespace(), e.key(), e.language(), e.variant(), e.text(), e.draftText(),
                    e.version(), e.updatedAt(), e.updatedBy());
        }
    }

    /** @param publish false stores the text as a draft (default true) */
    public record SaveMessageRequest(String namespace, String key, String language, String variant, String text,
                                     Boolean publish) {}

    public record PublishRequest(String namespace, String key, String language, String variant) {}

    private final MessageCatalogAdminService admin;
    private final MessageCatalog catalog;

    public I18nAdminController(MessageCatalogAdminService admin, MessageCatalog catalog) {
        this.admin = admin;
        this.catalog = catalog;
    }

    @GetMapping("/messages")
    @Operation(summary = "List / search messages")
    public Flux<MessageView> list(@RequestParam(required = false) String namespace,
                                  @RequestParam(required = false) String keyPrefix,
                                  @RequestParam(required = false) String language,
                                  @RequestParam(defaultValue = "false") boolean draftsOnly,
                                  @RequestParam(defaultValue = "100") int limit,
                                  @RequestParam(defaultValue = "0") int offset) {
        return admin.search(namespace, keyPrefix, language, draftsOnly, Math.max(1, Math.min(limit, 500)),
                Math.max(0, offset)).map(MessageView::of);
    }

    @PutMapping("/messages")
    @Operation(summary = "Create or edit a message (publish=false keeps it as a draft)")
    public Mono<MessageView> save(@RequestBody SaveMessageRequest r, @CurrentUser TntSecurityContext admin) {
        return this.admin.save(r.namespace(), r.key(), r.language(), r.variant(), r.text(),
                        !Boolean.FALSE.equals(r.publish()), actor(admin))
                .map(MessageView::of);
    }

    @PostMapping("/messages/publish")
    @Operation(summary = "Publish the pending draft of a message")
    public Mono<MessageView> publish(@RequestBody PublishRequest r, @CurrentUser TntSecurityContext admin) {
        return this.admin.publishDraft(r.namespace(), r.key(), r.language(), r.variant(), actor(admin))
                .map(MessageView::of);
    }

    @GetMapping("/messages/history")
    @Operation(summary = "Published versions of one message")
    public Flux<MessageVersion> history(@RequestParam String namespace, @RequestParam String key,
                                        @RequestParam String language,
                                        @RequestParam(required = false) String variant) {
        return admin.history(namespace, key, language, variant);
    }

    @GetMapping("/messages/missing")
    @Operation(summary = "Keys translated in one language but not in the other")
    public Flux<MissingTranslation> missing(@RequestParam(required = false) String namespace) {
        return admin.missing(namespace);
    }

    @PostMapping("/cache/reload")
    @Operation(summary = "Reload this instance's cache from the database now")
    public Mono<Map<String, Object>> reload() {
        return catalog.reload().then(Mono.fromSupplier(
                () -> Map.<String, Object>of("revision", catalog.revision(), "entries", catalog.size())));
    }

    private static String actor(TntSecurityContext ctx) {
        return ctx != null && ctx.userId() != null ? ctx.userId().toString() : "admin";
    }
}
