package com.yowyob.tiibntick.core.gofreelancer.domain;

import com.yowyob.tiibntick.core.delivery.domain.model.enums.AnnouncementStatus;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatus;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.deliveryNeed.DeliveryNeedStatusPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L2 — frozen correspondence table (announcement status, delivery status) → need status. Any change
 * of a row is a change of the status the mobile apps display: it must be decided, not drift.
 */
@DisplayName("Statut dérivé d'un besoin : table de correspondance figée")
class DeliveryNeedStatusPolicyTest {

    @ParameterizedTest(name = "annonce {0} + livraison {1} -> {2}")
    @CsvSource(nullValues = "NULL", value = {
            "NULL,           NULL,               PENDING",
            "DRAFT,          NULL,               PENDING",
            "PUBLISHED,      NULL,               PENDING",
            "IN_NEGOTIATION, NULL,               PENDING",
            "CANCELLED,      NULL,               CANCELLED",
            "COMPLETED,      NULL,               COMPLETED",
            "ASSIGNED,       NULL,               ASSIGNED",
            "ASSIGNED,       CREATED,            ASSIGNED",
            "ASSIGNED,       ASSIGNED,           ASSIGNED",
            "ASSIGNED,       PICKED_UP,          IN_PROGRESS",
            "ASSIGNED,       IN_TRANSIT,         IN_PROGRESS",
            "ASSIGNED,       AT_RELAY_HUB,       IN_PROGRESS",
            "ASSIGNED,       FAILED,             IN_PROGRESS",
            "ASSIGNED,       PAUSED_BY_INCIDENT, IN_PROGRESS",
            "ASSIGNED,       OUT_FOR_DELIVERY,   IN_PROGRESS",
            "ASSIGNED,       DELIVERY_ATTEMPT_FAILED, IN_PROGRESS",
            "ASSIGNED,       INCIDENT_REPORTED,  IN_PROGRESS",
            "ASSIGNED,       INCIDENT_HANDOVER,  IN_PROGRESS",
            "ASSIGNED,       DELIVERED,          COMPLETED",
            "ASSIGNED,       CANCELLED,          CANCELLED",
            "COMPLETED,      DELIVERED,          COMPLETED",
            "CANCELLED,      CANCELLED,          CANCELLED",
    })
    void table(AnnouncementStatus announcement, DeliveryStatus delivery, DeliveryNeedStatus expected) {
        assertThat(DeliveryNeedStatusPolicy.derive(announcement, delivery)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "annonce {0} + livraison {1} -> issue {2}")
    @CsvSource(nullValues = "NULL", value = {
            "COMPLETED,      DELIVERED,          DELIVERED",
            "COMPLETED,      FAILED,             FAILED",
            "COMPLETED,      NULL,               NULL",
            "ASSIGNED,       DELIVERED,          DELIVERED",
            "ASSIGNED,       FAILED,             NULL",
            "ASSIGNED,       IN_TRANSIT,         NULL",
            "CANCELLED,      CANCELLED,          NULL",
            "NULL,           NULL,               NULL",
    })
    @DisplayName("QO-9 : une demande COMPLETED expose son issue DELIVERED ou FAILED")
    void outcome(AnnouncementStatus announcement, DeliveryStatus delivery, String expected) {
        assertThat(DeliveryNeedStatusPolicy.outcome(announcement, delivery)).isEqualTo(expected);
    }

    @Test
    @DisplayName("toute combinaison a un statut ; ACCEPTED n'est jamais produit")
    void totalAndNeverAccepted() {
        Set<DeliveryNeedStatus> produced = new HashSet<>();
        Set<AnnouncementStatus> announcements = EnumSet.allOf(AnnouncementStatus.class);
        for (AnnouncementStatus a : announcements) {
            produced.add(DeliveryNeedStatusPolicy.derive(a, null));
            for (DeliveryStatus d : DeliveryStatus.values()) {
                DeliveryNeedStatus s = DeliveryNeedStatusPolicy.derive(a, d);
                assertThat(s).as(a + "/" + d).isNotNull();
                produced.add(s);
            }
        }
        assertThat(produced).doesNotContain(DeliveryNeedStatus.ACCEPTED)
                .containsExactlyInAnyOrder(DeliveryNeedStatus.PENDING, DeliveryNeedStatus.ASSIGNED,
                        DeliveryNeedStatus.IN_PROGRESS, DeliveryNeedStatus.COMPLETED, DeliveryNeedStatus.CANCELLED);
    }
}
