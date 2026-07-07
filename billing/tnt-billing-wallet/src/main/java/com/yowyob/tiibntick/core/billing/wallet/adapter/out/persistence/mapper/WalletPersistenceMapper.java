package com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.mapper;

import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.PaymentIntentEntity;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletEntity;
import com.yowyob.tiibntick.core.billing.wallet.adapter.out.persistence.entity.WalletTransactionEntity;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentChannel;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.PaymentIntentStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionType;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletOwnerType;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.WalletStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.enums.TransactionStatus;
import com.yowyob.tiibntick.core.billing.wallet.domain.model.*;
import org.mapstruct.*;

/**
 * MapStruct mapper — converts between Wallet domain objects and R2DBC entities.
 * All mappings are explicit to avoid unintended field bindings.
 *
 * @author MANFOUO Braun
 */
@Mapper(
    componentModel = "spring",
    unmappedTargetPolicy = ReportingPolicy.ERROR,
    imports = {
        WalletId.class,
        Money.class,
        java.util.Currency.class,
        WalletStatus.class,
        WalletOwnerType.class,
        TransactionId.class,
        TransactionType.class,
        PaymentChannel.class,
        TransactionStatus.class,
        PaymentIntentId.class,
        PaymentIntentStatus.class
    }
)
public interface WalletPersistenceMapper {

    // ── Wallet ────────────────────────────────────────────────────────────

    @Mapping(target = "id", expression = "java(wallet.getId().value())")
    @Mapping(target = "balance", expression = "java(wallet.getBalance().amount())")
    @Mapping(target = "reservedBalance", expression = "java(wallet.getReservedBalance().amount())")
    @Mapping(target = "currency", expression = "java(wallet.getCurrency().getCurrencyCode())")
    @Mapping(target = "status", expression = "java(wallet.getStatus().name())")
    @Mapping(target = "ownerType", expression = "java(wallet.getOwnerType() != null ? wallet.getOwnerType().name() : \"ACTOR\")")
    @Mapping(target = "ownerId", source = "ownerId")
    @Mapping(target = "version", source = "version")
    @Mapping(target = "isNew", ignore = true)
    WalletEntity toEntity(Wallet wallet);

    @Mapping(target = "id", expression = "java(WalletId.of(entity.getId()))")
    @Mapping(target = "balance", expression = "java(Money.of(entity.getBalance(), entity.getCurrency()))")
    @Mapping(target = "reservedBalance", expression = "java(Money.of(entity.getReservedBalance(), entity.getCurrency()))")
    @Mapping(target = "currency", expression = "java(Currency.getInstance(entity.getCurrency()))")
    @Mapping(target = "status", expression = "java(WalletStatus.valueOf(entity.getStatus()))")
    @Mapping(target = "ownerType", expression = "java(entity.getOwnerType() != null ? WalletOwnerType.valueOf(entity.getOwnerType()) : WalletOwnerType.ACTOR)")
    @Mapping(target = "ownerId", source = "ownerId")
    @Mapping(target = "transactions", ignore = true)
    @Mapping(target = "pendingCreditEvents", ignore = true)
    @Mapping(target = "pendingDebitEvents", ignore = true)
    Wallet toDomain(WalletEntity entity);

    // ── WalletTransaction ─────────────────────────────────────────────────

    @Mapping(target = "id", expression = "java(tx.getId().value())")
    @Mapping(target = "walletId", expression = "java(tx.getWalletId().value())")
    @Mapping(target = "amount", expression = "java(tx.getAmount().amount())")
    @Mapping(target = "balanceAfter", expression = "java(tx.getBalanceAfter().amount())")
    @Mapping(target = "currency", expression = "java(tx.getAmount().currencyCode())")
    @Mapping(target = "type", expression = "java(tx.getType().name())")
    @Mapping(target = "channel", expression = "java(tx.getChannel().name())")
    @Mapping(target = "status", expression = "java(tx.getStatus().name())")
    @Mapping(target = "isNew", ignore = true)
    WalletTransactionEntity toEntity(WalletTransaction tx);

    @Mapping(target = "id", expression = "java(TransactionId.of(entity.getId()))")
    @Mapping(target = "walletId", expression = "java(WalletId.of(entity.getWalletId()))")
    @Mapping(target = "amount", expression = "java(Money.of(entity.getAmount(), entity.getCurrency()))")
    @Mapping(target = "balanceAfter", expression = "java(Money.of(entity.getBalanceAfter(), entity.getCurrency()))")
    @Mapping(target = "type", expression = "java(TransactionType.valueOf(entity.getType()))")
    @Mapping(target = "channel", expression = "java(PaymentChannel.valueOf(entity.getChannel()))")
    @Mapping(target = "status", expression = "java(TransactionStatus.valueOf(entity.getStatus()))")
    WalletTransaction toDomain(WalletTransactionEntity entity);

    // ── PaymentIntent ─────────────────────────────────────────────────────

    @Mapping(target = "id", expression = "java(intent.getId().value())")
    @Mapping(target = "walletId", expression = "java(intent.getWalletId().value())")
    @Mapping(target = "amount", expression = "java(intent.getAmount().amount())")
    @Mapping(target = "currency", expression = "java(intent.getAmount().currencyCode())")
    @Mapping(target = "channel", expression = "java(intent.getChannel().name())")
    @Mapping(target = "status", expression = "java(intent.getStatus().name())")
    @Mapping(target = "isNew", ignore = true)
    PaymentIntentEntity toEntity(PaymentIntent intent);

    @Mapping(target = "id", expression = "java(PaymentIntentId.of(entity.getId()))")
    @Mapping(target = "walletId", expression = "java(WalletId.of(entity.getWalletId()))")
    @Mapping(target = "amount", expression = "java(Money.of(entity.getAmount(), entity.getCurrency()))")
    @Mapping(target = "channel", expression = "java(PaymentChannel.valueOf(entity.getChannel()))")
    @Mapping(target = "status", expression = "java(PaymentIntentStatus.valueOf(entity.getStatus()))")
    @Mapping(target = "metadata", ignore = true)
    PaymentIntent toDomain(PaymentIntentEntity entity);
}
