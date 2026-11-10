package com.yowyob.tiibntick.core.notify.application.service;

import com.yowyob.tiibntick.core.notify.domain.enums.DeliveryStatus;

/**
 * Maps the Kernel's delivery status ({@code PENDING, QUEUED, SENT, FAILED, CANCELLED}) onto TiiBnTick's
 * three-state {@link DeliveryStatus}.
 *
 * @author MANFOUO Braun
 */
public final class KernelDeliveryStatus {

    private KernelDeliveryStatus() {
    }

    public static DeliveryStatus fromKernel(String kernelStatus) {
        if (kernelStatus == null) {
            return DeliveryStatus.PENDING;
        }
        return switch (kernelStatus.trim().toUpperCase()) {
            case "SENT" -> DeliveryStatus.SENT;
            case "FAILED", "CANCELLED" -> DeliveryStatus.FAILED;
            default -> DeliveryStatus.PENDING;
        };
    }
}
