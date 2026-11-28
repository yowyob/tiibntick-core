package com.yowyob.tiibntick.delivery.domain;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.aggregate.DeliveryPerson;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryPersonStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Vetting state machine of a delivery person: every licit and illicit transition. */
class DeliveryPersonStatusTransitionTest {

    private static DeliveryPerson in(DeliveryPersonStatus status) {
        DeliveryPerson dp = DeliveryPerson.projection(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 10);
        // projection starts PENDING (vetting by an administrator); walk to the requested status through licit
        // transitions
        assertThat(dp.getStatus()).isEqualTo(DeliveryPersonStatus.PENDING);
        if (status != DeliveryPersonStatus.PENDING && status != DeliveryPersonStatus.REJECTED) dp.approve();
        switch (status) {
            case APPROVED -> { }
            case SUSPENDED -> dp.suspend();
            case REVOKED -> dp.revoke();
            case PENDING, REJECTED -> {
                dp = DeliveryPerson.builder().id(UUID.randomUUID()).status(DeliveryPersonStatus.PENDING).build();
                if (status == DeliveryPersonStatus.REJECTED) dp.reject();
            }
        }
        assertThat(dp.getStatus()).isEqualTo(status);
        return dp;
    }

    @ParameterizedTest(name = "{0} -> {1} allowed={2}")
    @CsvSource({
            "PENDING,APPROVED,true", "PENDING,REJECTED,true", "PENDING,SUSPENDED,false", "PENDING,REVOKED,false",
            "APPROVED,SUSPENDED,true", "APPROVED,REVOKED,true", "APPROVED,REJECTED,false", "APPROVED,PENDING,false",
            "SUSPENDED,APPROVED,true", "SUSPENDED,REVOKED,true", "SUSPENDED,REJECTED,false", "SUSPENDED,PENDING,false",
            "REJECTED,APPROVED,false", "REJECTED,PENDING,false", "REJECTED,REVOKED,false", "REJECTED,SUSPENDED,false",
            "REVOKED,APPROVED,false", "REVOKED,SUSPENDED,false", "REVOKED,PENDING,false", "REVOKED,REJECTED,false",
    })
    void transitions(DeliveryPersonStatus from, DeliveryPersonStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
        DeliveryPerson dp = in(from);
        if (allowed) {
            dp.transitionTo(to);
            assertThat(dp.getStatus()).isEqualTo(to);
        } else {
            assertThatThrownBy(() -> dp.transitionTo(to)).isInstanceOf(DeliveryDomainException.class);
            assertThat(dp.getStatus()).isEqualTo(from);
        }
    }

    @Test
    void sameStatusIsIdempotentEvenWhenTerminal() {
        for (DeliveryPersonStatus s : DeliveryPersonStatus.values()) {
            DeliveryPerson dp = in(s);
            dp.transitionTo(s);
            assertThat(dp.getStatus()).isEqualTo(s);
        }
        assertThat(DeliveryPersonStatus.REJECTED.isTerminal()).isTrue();
        assertThat(DeliveryPersonStatus.REVOKED.isTerminal()).isTrue();
        assertThat(DeliveryPersonStatus.SUSPENDED.isTerminal()).isFalse();
    }

    @Test
    void namedOperationsFollowTheMachine() {
        DeliveryPerson dp = in(DeliveryPersonStatus.APPROVED);
        dp.suspend();
        dp.approve();
        dp.revoke();
        assertThat(dp.getStatus()).isEqualTo(DeliveryPersonStatus.REVOKED);
        assertThat(dp.isAvailable()).isFalse();
        assertThatThrownBy(dp::approve).isInstanceOf(DeliveryDomainException.class);
    }
}
