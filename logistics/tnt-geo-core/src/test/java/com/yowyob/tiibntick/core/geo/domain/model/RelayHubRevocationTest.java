package com.yowyob.tiibntick.core.geo.domain.model;

import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decisions D-129, D-135, D-138, D-140: a relay hub is revoked for good, with a mandatory reason, only from APPROVED
 * or SUSPENDED; only an APPROVED hub takes new parcels, held parcels always leave.
 */
class RelayHubRevocationTest {

    private static final UUID ADMIN = UUID.randomUUID();

    private static RelayHub hub(HubStatus status, HubApprovalStatus approval, int occupancy) {
        Instant t = Instant.parse("2026-11-01T00:00:00Z");
        return RelayHub.rehydrate(UUID.randomUUID(), UUID.randomUUID(), null, RoadNodeId.generate(), 4, occupancy,
                "op", status, approval, t, t, null, null, null);
    }

    private static RelayHub approved(int occupancy) {
        return hub(HubStatus.ACTIVE, HubApprovalStatus.APPROVED, occupancy);
    }

    @ParameterizedTest(name = "{0}, operating {1}: revoked")
    @EnumSource(value = HubApprovalStatus.class, names = {"APPROVED", "SUSPENDED"})
    @DisplayName("APPROVED or SUSPENDED -> REVOKED: reason, date and administrator recorded, operating status kept")
    void revokeFromApprovedOrSuspended(HubApprovalStatus from) {
        for (HubStatus operating : HubStatus.values()) {
            RelayHub hub = hub(operating, from, operating == HubStatus.FULL ? 4 : 1);
            Instant before = Instant.now();

            hub.revoke("  Fraude constatée  ", ADMIN);

            assertThat(hub.approvalStatus()).isEqualTo(HubApprovalStatus.REVOKED);
            assertThat(hub.status()).isEqualTo(operating);
            assertThat(hub.isRevoked()).isTrue();
            assertThat(hub.isAvailable()).isFalse();
            assertThat(hub.revocationReason()).isEqualTo("Fraude constatée");
            assertThat(hub.revokedByActorId()).isEqualTo(ADMIN);
            assertThat(hub.revokedAt()).isAfterOrEqualTo(before);
            assertThat(hub.updatedAt()).isEqualTo(hub.revokedAt());
            assertThat(hub.currentOccupancy()).as("parcels in custody are not touched")
                    .isEqualTo(operating == HubStatus.FULL ? 4 : 1);
        }
    }

    @ParameterizedTest(name = "{0} -> REVOKED refused")
    @EnumSource(value = HubApprovalStatus.class, names = {"PENDING", "REJECTED", "REVOKED"})
    @DisplayName("D-135: a pending hub is rejected, not revoked; REJECTED and REVOKED are final")
    void revokeRefusedElsewhere(HubApprovalStatus from) {
        RelayHub hub = hub(HubStatus.ACTIVE, from, 0);
        assertThatThrownBy(() -> hub.revoke("reason", ADMIN)).isInstanceOf(RelayHubStateException.class);
        assertThat(hub.approvalStatus()).isEqualTo(from);
        assertThat(hub.revocationReason()).isNull();
    }

    @Test
    @DisplayName("REVOKED -> REVOKED refused: the revocation is not repeated and its reason is kept")
    void revokeTwiceRefused() {
        RelayHub hub = approved(0);
        hub.revoke("first", ADMIN);
        Instant at = hub.revokedAt();

        assertThatThrownBy(() -> hub.revoke("second", UUID.randomUUID())).isInstanceOf(RelayHubStateException.class);
        assertThat(hub.revocationReason()).isEqualTo("first");
        assertThat(hub.revokedAt()).isEqualTo(at);
    }

    @ParameterizedTest(name = "reason \"{0}\" refused")
    @ValueSource(strings = {"", "   ", "\t"})
    void blankReasonRefused(String reason) {
        RelayHub hub = approved(0);
        assertThatThrownBy(() -> hub.revoke(reason, ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThat(hub.approvalStatus()).isEqualTo(HubApprovalStatus.APPROVED);
    }

    @Test
    void nullReasonOrAdminRefused() {
        RelayHub hub = approved(0);
        assertThatThrownBy(() -> hub.revoke(null, ADMIN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hub.revoke("reason", null)).isInstanceOf(NullPointerException.class);
        assertThat(hub.approvalStatus()).isEqualTo(HubApprovalStatus.APPROVED);
    }

    @Test
    void tooLongReasonRefused() {
        RelayHub hub = approved(0);
        assertThatThrownBy(() -> hub.revoke("x".repeat(RelayHub.MAX_REASON_LENGTH + 1), ADMIN))
                .isInstanceOf(IllegalArgumentException.class);
        hub.revoke("x".repeat(RelayHub.MAX_REASON_LENGTH), ADMIN);
        assertThat(hub.isRevoked()).isTrue();
    }

    @ParameterizedTest(name = "on a revoked hub: {0} refused")
    @ValueSource(strings = {"reopen", "close", "permanentlyClose", "addParcel"})
    @DisplayName("a revoked hub is not reopened, closed again or given a parcel")
    void changesRefusedOnceRevoked(String operation) {
        RelayHub hub = approved(1);
        hub.revoke("reason", ADMIN);
        Consumer<RelayHub> op = switch (operation) {
            case "reopen" -> RelayHub::reopen;
            case "close" -> RelayHub::close;
            case "permanentlyClose" -> RelayHub::permanentlyClose;
            default -> RelayHub::addParcel;
        };
        assertThatThrownBy(() -> op.accept(hub)).isInstanceOf(RelayHubStateException.class)
                .isInstanceOf(IllegalStateException.class);
        assertThat(hub.approvalStatus()).isEqualTo(HubApprovalStatus.REVOKED);
        assertThat(hub.currentOccupancy()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}: no new parcel (D-140)")
    @EnumSource(value = HubApprovalStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("D-140: a hub that is not APPROVED takes no new parcel and is not proposed; held parcels leave")
    void notApprovedTakesNoParcel(HubApprovalStatus approval) {
        RelayHub hub = hub(HubStatus.ACTIVE, approval, 2);
        assertThat(hub.isAvailable()).isFalse();
        assertThat(hub.acceptsDeposits()).isFalse();
        assertThatThrownBy(hub::addParcel).isInstanceOf(RelayHubStateException.class);
        assertThat(hub.currentOccupancy()).isEqualTo(2);

        hub.removeParcel();
        hub.updateOccupancy(0);
        assertThat(hub.currentOccupancy()).isZero();
        assertThat(hub.approvalStatus()).isEqualTo(approval);
    }

    @Test
    @DisplayName("an approved open hub takes parcels and is proposed")
    void approvedTakesParcels() {
        RelayHub hub = approved(3);
        assertThat(hub.isAvailable()).isTrue();
        hub.addParcel();
        assertThat(hub.status()).isEqualTo(HubStatus.FULL);
        assertThat(hub.isAvailable()).as("full: not proposed").isFalse();
    }

    @Test
    @DisplayName("D-139: a pending hub is approved, never reactivated; a suspended one is reactivated, not approved")
    void approveAndReactivateAreDistinct() {
        RelayHub hub = hub(HubStatus.ACTIVE, HubApprovalStatus.PENDING, 0);
        assertThatThrownBy(hub::reactivate).isInstanceOf(RelayHubStateException.class);
        assertThat(hub.approvalStatus()).isEqualTo(HubApprovalStatus.PENDING);
        RelayHub suspended = hub(HubStatus.ACTIVE, HubApprovalStatus.SUSPENDED, 0);
        assertThatThrownBy(suspended::approve).isInstanceOf(RelayHubStateException.class);
        assertThat(suspended.approvalStatus()).isEqualTo(HubApprovalStatus.SUSPENDED);
    }

    @Test
    @DisplayName("the occupancy never reopens a closed hub")
    void occupancyKeepsAClosedHubClosed() {
        RelayHub hub = hub(HubStatus.PERMANENTLY_CLOSED, HubApprovalStatus.REVOKED, 2);
        hub.updateOccupancy(1);
        assertThat(hub.status()).isEqualTo(HubStatus.PERMANENTLY_CLOSED);
        RelayHub closed = hub(HubStatus.TEMPORARILY_CLOSED, HubApprovalStatus.APPROVED, 2);
        closed.updateOccupancy(4);
        assertThat(closed.status()).isEqualTo(HubStatus.TEMPORARILY_CLOSED);
    }

    @Test
    @DisplayName("the operating transitions are unchanged")
    void operatingTransitionsUnchanged() {
        RelayHub hub = approved(0);
        hub.close();
        assertThat(hub.status()).isEqualTo(HubStatus.TEMPORARILY_CLOSED);
        hub.reopen();
        assertThat(hub.status()).isEqualTo(HubStatus.ACTIVE);
        hub.permanentlyClose();
        assertThat(hub.status()).isEqualTo(HubStatus.PERMANENTLY_CLOSED);
        hub.reopen();
        assertThat(hub.status()).isEqualTo(HubStatus.ACTIVE);
        assertThat(hub.approvalStatus()).isEqualTo(HubApprovalStatus.APPROVED);
    }

    @Test
    @DisplayName("a new hub waits for its approval (D-138); a legacy rehydration is approved")
    void newHubIsPending() {
        RelayHub created = RelayHub.create(UUID.randomUUID(), null, RoadNodeId.generate(), 4, "op");
        assertThat(created.approvalStatus()).isEqualTo(HubApprovalStatus.PENDING);
        assertThat(created.isAvailable()).isFalse();
        Instant t = Instant.now();
        RelayHub legacy = RelayHub.rehydrate(UUID.randomUUID(), UUID.randomUUID(), null, RoadNodeId.generate(), 4, 0,
                "op", HubStatus.ACTIVE, t, t);
        assertThat(legacy.approvalStatus()).isEqualTo(HubApprovalStatus.APPROVED);
    }

    @Test
    void rehydrateKeepsTheRevocation() {
        Instant at = Instant.parse("2026-11-26T08:00:00Z");
        RelayHub hub = RelayHub.rehydrate(UUID.randomUUID(), UUID.randomUUID(), null, RoadNodeId.generate(), 4, 0, "op",
                HubStatus.PERMANENTLY_CLOSED, HubApprovalStatus.REVOKED, at, at, "reason", at, ADMIN);
        assertThat(hub.revocationReason()).isEqualTo("reason");
        assertThat(hub.revokedAt()).isEqualTo(at);
        assertThat(hub.revokedByActorId()).isEqualTo(ADMIN);
        assertThatThrownBy(hub::reopen).isInstanceOf(RelayHubStateException.class);
    }
}
