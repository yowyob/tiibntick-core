package com.yowyob.tiibntick.core.accounting.application.port.out;

import com.yowyob.tiibntick.core.accounting.domain.model.kernel.KernelEntryLink;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/** Local correspondences with the Kernel books (lot L6): accounts, journals, booked facts. */
public interface KernelBookkeepingLinkRepository {

    Mono<UUID> accountId(String ownerType, UUID ownerId, String code);

    /** Records the account; on a concurrent insert, returns the id already recorded. */
    Mono<UUID> saveAccount(String ownerType, UUID ownerId, String code, UUID kernelAccountId);

    Mono<UUID> journalId(String ownerType, UUID ownerId, String code);

    Mono<UUID> saveJournal(String ownerType, UUID ownerId, String code, UUID kernelJournalId);

    Mono<KernelEntryLink> findBySourceKey(String sourceKey);

    Mono<KernelEntryLink> findById(UUID id);

    Mono<KernelEntryLink> save(KernelEntryLink link, boolean isNew);

    Flux<KernelEntryLink> byStatus(KernelEntryLink.Status status, int limit);
}
