package com.yowyob.tiibntick.core.geo.domain.model;

import com.yowyob.tiibntick.core.geo.domain.exception.RelayHubStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static com.yowyob.tiibntick.core.geo.domain.model.HubApprovalStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Decision D-138: the approval of a relay hub follows the couriers' machine, checked on the whole product
 * states × states (25 pairs), on the enum and on the hub itself.
 */
class HubApprovalStatusTest {

    private static final Set<String> ALLOWED = Set.of(
            "PENDING>APPROVED", "PENDING>REJECTED",
            "APPROVED>SUSPENDED", "APPROVED>REVOKED",
            "SUSPENDED>APPROVED", "SUSPENDED>REVOKED");

    static Stream<Arguments> everyPair() {
        return Arrays.stream(values()).flatMap(from -> Arrays.stream(values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("everyPair")
    @DisplayName("the enum allows exactly the six transitions of the machine")
    void enumMachine(HubApprovalStatus from, HubApprovalStatus to) {
        assertThat(from.canTransitionTo(to)).isEqualTo(ALLOWED.contains(from + ">" + to));
    }

    @ParameterizedTest(name = "hub {0} -> {1}")
    @MethodSource("everyPair")
    @DisplayName("the hub applies the same machine; a refused transition changes nothing")
    void hubMachine(HubApprovalStatus from, HubApprovalStatus to) {
        RelayHub hub = hub(from);
        String reasonBefore = hub.revocationReason();
        boolean allowed = ALLOWED.contains(from + ">" + to);
        Runnable op = () -> apply(hub, from, to);
        if (allowed) {
            op.run();
            assertThat(hub.approvalStatus()).isEqualTo(to);
            assertThat(hub.isRevoked()).isEqualTo(to == REVOKED);
            assertThat(hub.acceptsDeposits()).isEqualTo(to == APPROVED);
        } else {
            assertThatThrownBy(op::run).isInstanceOf(RelayHubStateException.class);
            assertThat(hub.approvalStatus()).isEqualTo(from);
            assertThat(hub.revocationReason()).isEqualTo(reasonBefore);
        }
        assertThat(hub.status()).as("the operating status is separate").isEqualTo(HubStatus.ACTIVE);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("statuses")
    @DisplayName("only REJECTED and REVOKED are final; only APPROVED takes deposits (D-140)")
    void terminalAndDeposits(HubApprovalStatus s) {
        assertThat(s.isTerminal()).isEqualTo(s == REJECTED || s == REVOKED);
        assertThat(s.acceptsDeposits()).isEqualTo(s == APPROVED);
    }

    static Stream<HubApprovalStatus> statuses() {
        return Arrays.stream(values());
    }

    /** The hub operation that leads to {@code to}; APPROVED is reached by approve() from PENDING, else reactivate(). */
    private static void apply(RelayHub hub, HubApprovalStatus from, HubApprovalStatus to) {
        switch (to) {
            case APPROVED -> {
                if (from == SUSPENDED) hub.reactivate(); else hub.approve();
            }
            case REJECTED -> hub.reject();
            case SUSPENDED -> hub.suspend();
            case REVOKED -> hub.revoke("reason", UUID.randomUUID());
            case PENDING -> throw new RelayHubStateException("no operation leads back to PENDING");
        }
    }

    static RelayHub hub(HubApprovalStatus approval) {
        Instant t = Instant.parse("2026-11-01T00:00:00Z");
        boolean revoked = approval == REVOKED;
        return RelayHub.rehydrate(UUID.randomUUID(), UUID.randomUUID(), null, RoadNodeId.generate(), 4, 1, "op",
                HubStatus.ACTIVE, approval, t, t, revoked ? "old" : null, revoked ? t : null,
                revoked ? UUID.randomUUID() : null);
    }
}
