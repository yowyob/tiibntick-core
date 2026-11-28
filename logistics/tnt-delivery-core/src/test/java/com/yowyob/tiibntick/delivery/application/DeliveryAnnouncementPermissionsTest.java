package com.yowyob.tiibntick.delivery.application;

import com.yowyob.tiibntick.core.delivery.application.port.in.command.UpdateDeliveryAnnouncementCommand;
import com.yowyob.tiibntick.core.delivery.application.service.DeliveryAnnouncementService;
import com.yowyob.tiibntick.core.roles.adapter.in.web.RequirePermission;
import com.yowyob.tiibntick.core.roles.domain.model.TntPermission;
import com.yowyob.tiibntick.core.roles.domain.model.TntRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L2 (m1) — editing an announcement is guarded by {@code announcement:update}, not
 * {@code announcement:create}; the client who publishes announcements holds it, and no role able to
 * publish loses the right to edit.
 */
@DisplayName("Permissions de modification d'une annonce")
class DeliveryAnnouncementPermissionsTest {

    @Test
    @DisplayName("updateAnnouncement exige announcement:update")
    void updateRequiresAnnouncementUpdate() throws Exception {
        RequirePermission p = DeliveryAnnouncementService.class
                .getMethod("updateAnnouncement", UpdateDeliveryAnnouncementCommand.class)
                .getAnnotation(RequirePermission.class);

        assertThat(p).isNotNull();
        assertThat(p.resource() + ":" + p.action()).isEqualTo(TntPermission.ANNOUNCEMENT_UPDATE);
    }

    @Test
    @DisplayName("CLIENT a announcement:update")
    void clientHoldsAnnouncementUpdate() {
        assertThat(TntRole.CLIENT.defaultPermissions()).contains(TntPermission.ANNOUNCEMENT_UPDATE);
    }

    @Test
    @DisplayName("tout rôle qui peut publier une annonce peut aussi la modifier")
    void everyPublisherCanEdit() {
        Arrays.stream(TntRole.values())
                .filter(r -> r.defaultPermissions().contains(TntPermission.ANNOUNCEMENT_CREATE))
                .forEach(r -> assertThat(r.defaultPermissions()).as(r.name())
                        .contains(TntPermission.ANNOUNCEMENT_UPDATE));
    }
}
