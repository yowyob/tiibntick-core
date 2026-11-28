package com.yowyob.tiibntick.core.accounting.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.accounting.application.port.out.KernelBookkeepingLinkRepository;
import com.yowyob.tiibntick.core.accounting.application.port.out.KernelBookkeepingPort;
import com.yowyob.tiibntick.core.accounting.application.port.out.KernelBookkeepingPort.KernelLine;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.BookkeepingFact;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.KernelEntryLink;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.KernelEntryLink.Status;
import com.yowyob.tiibntick.core.accounting.domain.service.BookkeepingFactPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

/**
 * Books the money facts in the Kernel (lot L6, cible §4.6, decisions H.3, QO-20, QO-21). For each fact:
 * <ol>
 *   <li>one row per {@code sourceKey} in {@code accounting.kernel_entry_links}: a replayed event finds it and does
 *       nothing more once {@code VALIDATED} or {@code SKIPPED};</li>
 *   <li>the book's journal and accounts are looked up in the local correspondences, else found or created in the
 *       Kernel (account {@code externalId} = name UUID of {@code <ownerType>:<ownerId>:<code>});</li>
 *   <li>the entry is created ({@code POSTED}) then validated ({@code VALIDATED}). A row left {@code PENDING} or
 *       {@code FAILED} by an earlier attempt first looks the entry up by its reference, so a crash between the
 *       Kernel call and the record never books twice.</li>
 * </ol>
 * The platform book is the organisation {@code tnt.accounting.kernel.platform-organization-id}; not configured → the
 * fact is kept {@code FAILED} ({@code PLATFORM_ORGANIZATION_NOT_CONFIGURED}). A refusal of the Kernel (4xx) is kept
 * {@code FAILED} without retry; an unavailable Kernel is kept {@code FAILED} and the error propagates (the Kafka
 * listener retries, then the DLQ). The administrator lists and replays the failed facts (admin spec §5.6).
 * The local journal ({@code accounting.journal_entries}) is no longer written (QO-21).
 */
@Slf4j
@Service
public class KernelBookkeepingService {

    public static final String PLATFORM_ORGANIZATION_NOT_CONFIGURED = "PLATFORM_ORGANIZATION_NOT_CONFIGURED";
    public static final String KERNEL_REFUSED = "KERNEL_REFUSED";
    public static final String KERNEL_UNAVAILABLE = "KERNEL_UNAVAILABLE";

    private final KernelBookkeepingPort kernel;
    private final KernelBookkeepingLinkRepository links;
    private final UUID platformOrganization;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Autowired
    public KernelBookkeepingService(KernelBookkeepingPort kernel, KernelBookkeepingLinkRepository links,
                                    @Value("${tnt.accounting.kernel.platform-organization-id:}") String platform) {
        this(kernel, links, platform == null || platform.isBlank() ? null : UUID.fromString(platform.trim()),
                Clock.systemUTC());
    }

    public KernelBookkeepingService(KernelBookkeepingPort kernel, KernelBookkeepingLinkRepository links,
                                    UUID platformOrganization, Clock clock) {
        this.kernel = kernel;
        this.links = links;
        this.platformOrganization = platformOrganization;
        this.clock = clock;
    }

    /** Books the fact once; {@code null} → nothing. */
    public Mono<KernelEntryLink> book(BookkeepingFact fact) {
        if (fact == null) {
            return Mono.empty();
        }
        return links.findBySourceKey(fact.sourceKey())
                .map(existing -> new Attempt(existing, false))
                .switchIfEmpty(Mono.defer(() -> create(fact)))
                .flatMap(a -> a.link().status().done() ? Mono.just(a.link()) : attempt(a.link(), fact, a.fresh()));
    }

    /** Administrator: replays a fact not booked (FAILED, or left PENDING/POSTED). */
    public Mono<KernelEntryLink> retry(UUID linkId) {
        return links.findById(linkId)
                .switchIfEmpty(Mono.error(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown accounting fact " + linkId)))
                .flatMap(link -> link.status().done() ? Mono.just(link)
                        : Mono.fromCallable(() -> json.readValue(link.fact(), BookkeepingFact.class))
                        .flatMap(fact -> attempt(link, fact, false)));
    }

    public Flux<KernelEntryLink> list(Status status, int limit) {
        return links.byStatus(status, Math.max(1, Math.min(limit, 500)));
    }

    private record Attempt(KernelEntryLink link, boolean fresh) {
    }

    private Mono<Attempt> create(BookkeepingFact fact) {
        var now = clock.instant();
        String serialized;
        try {
            serialized = json.writeValueAsString(fact);
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
        KernelEntryLink link = new KernelEntryLink(UUID.randomUUID(), fact.sourceKey(), fact.sourceType(),
                fact.sourceEventId(), fact.ownerType(), fact.ownerId(), fact.reference(), fact.amount(),
                fact.currency(), serialized, null, fact.skipped() ? Status.SKIPPED : Status.PENDING,
                fact.skipReason(), null, now, now);
        if (fact.skipped()) {
            log.info("ACCOUNTING_FACT_SKIPPED source={} reason={}", fact.sourceKey(), fact.skipReason());
        }
        return links.save(link, true)
                .map(saved -> new Attempt(saved, true))
                // a concurrent delivery of the same event recorded it first
                .onErrorResume(DataIntegrityViolationException.class, e -> links.findBySourceKey(fact.sourceKey())
                        .map(existing -> new Attempt(existing, false)));
    }

    private Mono<KernelEntryLink> attempt(KernelEntryLink link, BookkeepingFact fact, boolean fresh) {
        boolean platform = BookkeepingFact.PLATFORM.equals(fact.ownerType());
        UUID owner = platform ? platformOrganization : fact.ownerId();
        if (owner == null) {
            log.error("ACCOUNTING_NOT_BOOKED source={} reason={} — the platform book has no Kernel organisation "
                    + "(tnt.accounting.kernel.platform-organization-id); replay after setting it (admin spec §5.6)",
                    fact.sourceKey(), PLATFORM_ORGANIZATION_NOT_CONFIGURED);
            return links.save(link.with(Status.FAILED, null, PLATFORM_ORGANIZATION_NOT_CONFIGURED, clock.instant()),
                    false);
        }
        KernelEntryLink owned = link.ownerId() == null ? link.withOwner(owner, clock.instant()) : link;
        Mono<KernelBookkeepingPort.KernelEntry> existing = owned.kernelEntryId() != null
                ? Mono.just(new KernelBookkeepingPort.KernelEntry(owned.kernelEntryId(),
                owned.status() == Status.POSTED ? "POSTED" : null))
                : fresh ? Mono.empty() : kernel.findEntryByReference(owner, fact.reference());
        return existing
                .switchIfEmpty(Mono.defer(() -> post(owner, fact)))
                .flatMap(entry -> links.save(owned.with(Status.POSTED, entry.id(), null, clock.instant()), false)
                        .flatMap(posted -> "VALIDATED".equals(entry.status()) ? Mono.just(entry)
                                : kernel.validateEntry(owner, entry.id()))
                        .flatMap(validated -> links.save(owned.with(Status.VALIDATED, entry.id(), null,
                                clock.instant()), false)))
                .doOnNext(done -> log.info("ACCOUNTING_BOOKED source={} book={}:{} entry={}", fact.sourceKey(),
                        fact.ownerType(), owner, done.kernelEntryId()))
                .onErrorResume(e -> failed(owned, fact, e));
    }

    private Mono<KernelBookkeepingPort.KernelEntry> post(UUID owner, BookkeepingFact fact) {
        String type = fact.ownerType();
        return journal(type, owner, fact)
                .flatMap(journal -> Flux.fromIterable(fact.lines())
                        .concatMap(l -> account(type, owner, l)
                                .map(account -> new KernelLine(account, l.debit(), l.credit(), l.label())))
                        .collectList()
                        .flatMap(lines -> kernel.postEntry(owner, journal, fact.reference(), fact.date(), lines)));
    }

    private Mono<UUID> journal(String type, UUID owner, BookkeepingFact fact) {
        return links.journalId(type, owner, fact.journalCode())
                .switchIfEmpty(Mono.defer(() -> kernel.findOrCreateJournal(owner, fact.journalCode(),
                                fact.journalLabel(), BookkeepingFactPolicy.JOURNAL_TYPE)
                        .flatMap(id -> links.saveJournal(type, owner, fact.journalCode(), id))));
    }

    private Mono<UUID> account(String type, UUID owner, BookkeepingFact.Line line) {
        return links.accountId(type, owner, line.accountCode())
                .switchIfEmpty(Mono.defer(() -> kernel.findOrCreateAccount(owner, line.accountCode(),
                                line.accountLabel(), line.accountType(), externalId(type, owner, line.accountCode()))
                        .flatMap(id -> links.saveAccount(type, owner, line.accountCode(), id))));
    }

    static UUID externalId(String ownerType, UUID owner, String code) {
        return UUID.nameUUIDFromBytes((ownerType + ":" + owner + ":" + code).getBytes(StandardCharsets.UTF_8));
    }

    private Mono<KernelEntryLink> failed(KernelEntryLink link, BookkeepingFact fact, Throwable e) {
        boolean refused = e instanceof WebClientResponseException w && w.getStatusCode().is4xxClientError();
        String reason = refused
                ? KERNEL_REFUSED + ": " + ((WebClientResponseException) e).getStatusCode().value()
                : KERNEL_UNAVAILABLE + ": " + e.getClass().getSimpleName();
        log.error("ACCOUNTING_NOT_BOOKED source={} reason={} — listed to the administrator (admin spec §5.6): {}",
                fact.sourceKey(), reason, e.toString());
        // the last known Kernel entry is kept, so that a replay validates it instead of posting again
        return links.findById(link.id()).defaultIfEmpty(link)
                .flatMap(current -> links.save(current.with(Status.FAILED, current.kernelEntryId(),
                        reason.length() > 250 ? reason.substring(0, 250) : reason, clock.instant()), false))
                .flatMap(saved -> refused ? Mono.just(saved) : Mono.error(e));
    }
}
