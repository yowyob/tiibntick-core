package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.PacketDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.ParcelDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot L1 (architecture-cible 3.2 h): the API {@code PacketDTO} ↔ the core {@code PackageSpecification}, through
 * the gofp {@link ParcelDetails}. Designation and thickness are carried both ways; the DTO keeps its names.
 */
class PacketParcelMappingTest {

    private static PacketDTO packet() {
        PacketDTO dto = new PacketDTO();
        dto.setDesignation("Shoes");
        dto.setDescription("A pair of shoes");
        dto.setWeight(2.5);
        dto.setWidth(30.0);
        dto.setHeight(20.0);
        dto.setLength(12.0);
        dto.setThickness(4.0);
        dto.setFragile(true);
        dto.setIsPerishable(false);
        dto.setPhotoPacket("https://img/1.jpg");
        return dto;
    }

    @Test
    @DisplayName("PacketDTO → parcel: every field, designation and thickness included")
    void toParcel() {
        ParcelDetails details = DeliveryNeedApplicationService.toParcelDetails(packet());
        assertThat(details).isEqualTo(new ParcelDetails(null, "Shoes", "A pair of shoes", 2.5, 30.0, 20.0, 12.0, 4.0,
                true, false, "https://img/1.jpg"));
        assertThat(com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery.ParcelPortAdapterAccess.spec(details))
                .isEqualTo(new PackageSpecification(2.5, 30, 20, 12, true, false, "A pair of shoes", "Shoes", 4.0));
    }

    @Test
    @DisplayName("parcel → PacketDTO: the client reads back what it sent")
    void roundTrip() {
        ParcelDetails stored = new ParcelDetails(UUID.randomUUID(), "Shoes", "A pair of shoes", 2.5, 30.0, 20.0, 12.0,
                4.0, true, false, "https://img/1.jpg");
        assertThat(DeliveryNeedApplicationService.toPacketDTO(stored)).isEqualTo(packet());
    }
}
