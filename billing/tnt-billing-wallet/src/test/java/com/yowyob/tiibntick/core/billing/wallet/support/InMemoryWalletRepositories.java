package com.yowyob.tiibntick.core.billing.wallet.support;

import com.yowyob.tiibntick.core.billing.wallet.application.port.out.ISettlementRepository;
import com.yowyob.tiibntick.core.billing.wallet.application.port.out.IWalletHoldRepository;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.CommissionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.HoldStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletHold;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.WalletSettlement;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory fakes of the settlement/hold repositories for service-level tests. */
public final class InMemoryWalletRepositories {

    private InMemoryWalletRepositories() {
    }

    public static final class Settlements implements ISettlementRepository {
        private final Map<UUID, WalletSettlement> store = new ConcurrentHashMap<>();

        @Override
        public Mono<WalletSettlement> save(WalletSettlement s) {
            WalletSettlement saved = s.toBuilder().persisted(true).build();
            store.put(saved.getId(), saved);
            return Mono.just(saved);
        }

        @Override
        public Mono<WalletSettlement> findById(UUID id) {
            return Mono.justOrEmpty(store.get(id));
        }

        @Override
        public Mono<WalletSettlement> findByReference(UUID tenantId, String type, UUID refId) {
            return Flux.fromIterable(store.values())
                    .filter(s -> s.getTenantId().equals(tenantId) && s.getReferenceType().equals(type)
                            && s.getReferenceId().equals(refId))
                    .next();
        }

        @Override
        public Mono<WalletSettlement> findByReference(String type, UUID refId) {
            return Flux.fromIterable(store.values())
                    .filter(s -> s.getReferenceType().equals(type) && s.getReferenceId().equals(refId))
                    .next();
        }

        @Override
        public Flux<WalletSettlement> findByTenantAndCommissionStatus(UUID tenantId, CommissionStatus status) {
            return Flux.fromIterable(store.values())
                    .filter(s -> s.getTenantId().equals(tenantId) && s.getCommissionStatus() == status);
        }
    }

    public static final class Holds implements IWalletHoldRepository {
        private final Map<UUID, WalletHold> store = new ConcurrentHashMap<>();

        @Override
        public Mono<WalletHold> save(WalletHold h) {
            WalletHold saved = h.toBuilder().persisted(true).build();
            store.put(saved.getId(), saved);
            return Mono.just(saved);
        }

        @Override
        public Flux<WalletHold> findActiveByOwner(UUID ownerUserId) {
            return Flux.fromIterable(store.values())
                    .filter(h -> h.getOwnerUserId().equals(ownerUserId) && h.getStatus() == HoldStatus.ACTIVE);
        }

        @Override
        public Flux<WalletHold> findActiveByReference(String type, UUID refId) {
            return Flux.fromIterable(store.values())
                    .filter(h -> h.getReferenceType().equals(type) && h.getReferenceId().equals(refId)
                            && h.getStatus() == HoldStatus.ACTIVE);
        }
    }
}
