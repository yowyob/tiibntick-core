package com.yowyob.tiibntick.core.notify.infrastructure.adapter.kernel;

import com.yowyob.tiibntick.core.notify.domain.enums.NotificationChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The six channels the Kernel declares ({@code EMAIL, SMS, WHATSAPP, PUSH, WEBSOCKET, IN_APP}) are all readable. */
class KernelChannelMapperTest {

    @Test
    void everyChannelTheCoreSendsOnRoundTrips() {
        for (NotificationChannel channel : NotificationChannel.values()) {
            assertThat(KernelChannelMapper.fromKernel(KernelChannelMapper.toKernel(channel))).isEqualTo(channel);
        }
    }

    @Test
    void theKernelInboxChannelIsReadAsInApp_andDoesNotBreakAList() {
        assertThat(KernelChannelMapper.fromKernel("IN_APP")).isEqualTo(NotificationChannel.IN_APP_WEBSOCKET);
        assertThat(KernelChannelMapper.fromKernel("WEBSOCKET")).isEqualTo(NotificationChannel.IN_APP_WEBSOCKET);
    }

    @Test
    void anUnknownChannelIsStillRefused() {
        assertThatThrownBy(() -> KernelChannelMapper.fromKernel("TELEGRAM"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
