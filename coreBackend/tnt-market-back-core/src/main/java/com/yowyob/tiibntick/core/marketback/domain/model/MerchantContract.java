package com.yowyob.tiibntick.core.marketback.domain.model;

import com.yowyob.tiibntick.core.marketback.domain.event.MerchantContractSignedEvent;
import com.yowyob.tiibntick.core.marketback.domain.exception.MarketDomainException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate Root — MerchantContract (Volume contract between merchant and provider).
 * @author MANFOUO Braun
 */
public class MerchantContract {

    private final ContractId id;
    private final String tenantId;
    private final UUID merchantId;
    private final UUID providerId;
    private final MarketListingId listingId;

    private ContractStatus status;
    private ContractTerms terms;
    private List<VolumeTier> volumeTiers;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean renewalOption;
    private int totalOrdersExecuted;
    private long totalAmountXaf;
    private boolean merchantSigned;
    private boolean providerSigned;

    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime signedAt;
    private final List<Object> domainEvents = new ArrayList<>();

    public static MerchantContract negotiate(
            String tenantId, UUID merchantId, UUID providerId,
            MarketListingId listingId, ContractTerms terms,
            List<VolumeTier> tiers, LocalDate startDate, LocalDate endDate) {
        return new MerchantContract(tenantId, merchantId, providerId, listingId, terms, tiers, startDate, endDate);
    }

    private MerchantContract(String tenantId, UUID merchantId, UUID providerId,
            MarketListingId listingId, ContractTerms terms, List<VolumeTier> tiers,
            LocalDate startDate, LocalDate endDate) {
        this.id = ContractId.generate();
        this.tenantId = tenantId;
        this.merchantId = merchantId;
        this.providerId = providerId;
        this.listingId = listingId;
        this.terms = terms;
        this.volumeTiers = new ArrayList<>(tiers);
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = ContractStatus.NEGOTIATING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    private String terminationReason;

    MerchantContract() {
        this.id = null; this.tenantId = null; this.merchantId = null;
        this.providerId = null; this.listingId = null; this.createdAt = null;
    }

    /** Reconstitutes a MerchantContract from its persisted state. */
    public static MerchantContract reconstitute(
            ContractId id, String tenantId, UUID merchantId, UUID providerId,
            MarketListingId listingId, ContractStatus status,
            ContractTerms terms, List<VolumeTier> volumeTiers,
            String terminationReason, LocalDate startDate, LocalDate endDate,
            LocalDateTime signedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new MerchantContract(id, tenantId, merchantId, providerId, listingId,
                status, terms, volumeTiers, terminationReason, startDate, endDate,
                signedAt, createdAt, updatedAt);
    }

    private MerchantContract(
            ContractId id, String tenantId, UUID merchantId, UUID providerId,
            MarketListingId listingId, ContractStatus status,
            ContractTerms terms, List<VolumeTier> volumeTiers,
            String terminationReason, LocalDate startDate, LocalDate endDate,
            LocalDateTime signedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.merchantId = merchantId;
        this.providerId = providerId;
        this.listingId = listingId;
        this.status = status;
        this.terms = terms;
        this.volumeTiers = volumeTiers != null ? new ArrayList<>(volumeTiers) : new ArrayList<>();
        this.terminationReason = terminationReason;
        this.startDate = startDate;
        this.endDate = endDate;
        this.signedAt = signedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public void signByMerchant(UUID merchantId) {
        this.merchantSigned = true;
        checkAndActivate();
        this.updatedAt = LocalDateTime.now();
    }

    public void countersignByProvider(UUID providerId) {
        this.providerSigned = true;
        checkAndActivate();
        this.updatedAt = LocalDateTime.now();
    }

    private void checkAndActivate() {
        if (merchantSigned && providerSigned) {
            this.status = ContractStatus.ACTIVE;
            this.signedAt = LocalDateTime.now();
            domainEvents.add(new MerchantContractSignedEvent(id, merchantId, providerId, signedAt));
        } else {
            this.status = ContractStatus.AWAITING_SIGNATURES;
        }
    }

    public void terminate(String reason) {
        this.status = ContractStatus.TERMINATED;
        this.updatedAt = LocalDateTime.now();
    }

    public void renew(LocalDate newEndDate) {
        if (status != ContractStatus.ACTIVE && status != ContractStatus.EXPIRED) {
            throw new MarketDomainException("Contract must be ACTIVE or EXPIRED to renew.");
        }
        this.endDate = newEndDate;
        this.status = ContractStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void recordExecution(Money orderAmount) {
        this.totalOrdersExecuted++;
        this.totalAmountXaf += orderAmount.amount();
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == ContractStatus.ACTIVE
                && (endDate == null || !LocalDate.now().isAfter(endDate));
    }

    public VolumeTier currentTier() {
        return volumeTiers.stream()
                .filter(t -> t.appliesTo(totalOrdersExecuted))
                .findFirst().orElse(null);
    }

    public List<Object> pullDomainEvents() {
        List<Object> evts = List.copyOf(domainEvents);
        domainEvents.clear();
        return evts;
    }

    public String getTerminationReason() { return terminationReason; }
    public ContractId getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getMerchantId() { return merchantId; }
    public UUID getProviderId() { return providerId; }
    public MarketListingId getListingId() { return listingId; }
    public ContractStatus getStatus() { return status; }
    public ContractTerms getTerms() { return terms; }
    public List<VolumeTier> getVolumeTiers() { return List.copyOf(volumeTiers); }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isRenewalOption() { return renewalOption; }
    public int getTotalOrdersExecuted() { return totalOrdersExecuted; }
    public long getTotalAmountXaf() { return totalAmountXaf; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public LocalDateTime getSignedAt() { return signedAt; }
}
