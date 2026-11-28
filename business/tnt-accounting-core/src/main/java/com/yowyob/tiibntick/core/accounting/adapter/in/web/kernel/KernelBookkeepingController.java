package com.yowyob.tiibntick.core.accounting.adapter.in.web.kernel;

import com.yowyob.tiibntick.core.accounting.application.service.KernelBookkeepingService;
import com.yowyob.tiibntick.core.accounting.domain.model.kernel.KernelEntryLink;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Administration of the facts booked in the Kernel (lot L6, admin spec §5.6): list by status (the {@code FAILED} ones
 * first of all) and replay one.
 */
@RestController
@RequestMapping("/api/v1/accounting/kernel-entries")
public class KernelBookkeepingController {

    private final KernelBookkeepingService bookkeeping;

    public KernelBookkeepingController(KernelBookkeepingService bookkeeping) {
        this.bookkeeping = bookkeeping;
    }

    public record EntryView(UUID id, String sourceKey, String sourceType, String sourceEventId, String ownerType,
                            UUID ownerId, String reference, BigDecimal amount, String currency, UUID kernelEntryId,
                            String status, String reason, Instant postedAt, Instant createdAt, Instant updatedAt) {
        static EntryView of(KernelEntryLink l) {
            return new EntryView(l.id(), l.sourceKey(), l.sourceType(), l.sourceEventId(), l.ownerType(),
                    l.ownerId(), l.reference(), l.amount(), l.currency(), l.kernelEntryId(), l.status().name(),
                    l.reason(), l.postedAt(), l.createdAt(), l.updatedAt());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('accounting:admin')")
    public Flux<EntryView> list(@RequestParam(required = false) KernelEntryLink.Status status,
                                @RequestParam(defaultValue = "100") int limit) {
        return bookkeeping.list(status, limit).map(EntryView::of);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('accounting:admin')")
    public Mono<EntryView> retry(@PathVariable UUID id) {
        return bookkeeping.retry(id).map(EntryView::of);
    }
}
