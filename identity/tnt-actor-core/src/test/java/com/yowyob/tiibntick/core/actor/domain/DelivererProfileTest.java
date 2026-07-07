package com.yowyob.tiibntick.core.actor.domain;

import com.yowyob.tiibntick.core.actor.domain.exception.DelivererAlreadyOnMissionException;
import com.yowyob.tiibntick.core.actor.domain.model.ActorLocation;
import com.yowyob.tiibntick.core.actor.domain.model.ActorRating;
import com.yowyob.tiibntick.core.actor.domain.model.ActorStatus;
import com.yowyob.tiibntick.core.actor.domain.model.ActorType;
import com.yowyob.tiibntick.core.actor.domain.model.Badge;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererProfile;
import com.yowyob.tiibntick.core.actor.domain.model.DelivererType;
import com.yowyob.tiibntick.core.actor.domain.model.KycStatus;
import com.yowyob.tiibntick.core.actor.domain.model.LocationSource;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DelivererProfileTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ACTOR_ID  = UUID.randomUUID();
    private static final UUID AGENCY_ID = UUID.randomUUID();
    private static final UUID BRANCH_ID = UUID.randomUUID();

    @Test
    void create_shouldInitializeWithDefaultsAndInactiveStatus() {
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT);

        assertThat(profile.id()).isNotNull();
        assertThat(profile.tenantId()).isEqualTo(TENANT_ID);
        assertThat(profile.actorId()).isEqualTo(ACTOR_ID);
        assertThat(profile.actorType()).isEqualTo(ActorType.PERMANENT_DELIVERER);
        assertThat(profile.actorStatus()).isEqualTo(ActorStatus.INACTIVE);
        assertThat(profile.kycStatus()).isEqualTo(KycStatus.PENDING);
        assertThat(profile.rating()).isEqualTo(ActorRating.zero());
        assertThat(profile.badges()).isEmpty();
        assertThat(profile.hasLocation()).isFalse();
        assertThat(profile.isAvailableForMission()).isFalse();
        assertThat(profile.hasActiveMission()).isFalse();
    }

    @Test
    void activate_shouldSetStatusToActive() {
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT)
                .activate();

        assertThat(profile.actorStatus()).isEqualTo(ActorStatus.ACTIVE);
        assertThat(profile.isAvailableForMission()).isTrue();
    }

    @Test
    void assignMission_whenAvailable_shouldSetMissionId() {
        UUID missionId = UUID.randomUUID();
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT)
                .activate()
                .assignMission(missionId);

        assertThat(profile.missionActiveId()).isEqualTo(missionId);
        assertThat(profile.hasActiveMission()).isTrue();
        assertThat(profile.isAvailableForMission()).isFalse();
    }

    @Test
    void assignMission_whenAlreadyOnMission_shouldThrow() {
        UUID firstMissionId = UUID.randomUUID();
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT)
                .activate()
                .assignMission(firstMissionId);

        assertThatThrownBy(() -> profile.assignMission(UUID.randomUUID()))
                .isInstanceOf(DelivererAlreadyOnMissionException.class);
    }

    @Test
    void releaseMission_shouldClearMissionId() {
        UUID missionId = UUID.randomUUID();
        DelivererProfile released = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT)
                .activate()
                .assignMission(missionId)
                .releaseMission();

        assertThat(released.missionActiveId()).isNull();
        assertThat(released.isAvailableForMission()).isTrue();
    }

    @Test
    void withLocation_shouldUpdateLocationImmutably() {
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT);
        ActorLocation location = ActorLocation.gps(3.848, 11.502, 10.0);

        DelivererProfile updated = profile.withLocation(location);

        assertThat(profile.hasLocation()).isFalse();
        assertThat(updated.hasLocation()).isTrue();
        assertThat(updated.currentLocation().latitude()).isEqualTo(3.848);
        assertThat(updated.currentLocation().source()).isEqualTo(LocationSource.GPS);
    }

    @Test
    void withRating_shouldUpdateRatingImmutably() {
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT);
        ActorRating newRating = profile.rating().addRating(4.5);

        DelivererProfile updated = profile.withRating(newRating);

        assertThat(profile.rating().totalRatings()).isZero();
        assertThat(updated.rating().totalRatings()).isEqualTo(1);
        assertThat(updated.rating().score()).isEqualTo(4.5);
    }

    @Test
    void withBadge_shouldAddBadge() {
        Badge badge = Badge.earn("KYC_VERIFIED", "KYC Verified");
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT)
                .withBadge(badge);

        assertThat(profile.badges()).hasSize(1);
        assertThat(profile.badges()).anyMatch(b -> b.code().equals("KYC_VERIFIED"));
    }

    @Test
    void create_withInvalidCapacity_shouldThrow() {
        assertThatThrownBy(() ->
                DelivererProfile.create(TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, -5.0, DelivererType.PERMANENT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capacityKg must be positive");
    }

    @Test
    void withKycStatus_shouldUpdateKycImmutably() {
        DelivererProfile profile = DelivererProfile.create(
                TENANT_ID, ACTOR_ID, AGENCY_ID, BRANCH_ID, 50.0, DelivererType.PERMANENT)
                .withKycStatus(KycStatus.VERIFIED);

        assertThat(profile.kycStatus()).isEqualTo(KycStatus.VERIFIED);
        assertThat(profile.isKycVerified()).isTrue();
    }
}
