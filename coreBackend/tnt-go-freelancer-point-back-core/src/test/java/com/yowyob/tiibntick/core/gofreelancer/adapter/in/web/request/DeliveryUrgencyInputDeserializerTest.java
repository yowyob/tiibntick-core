package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.delivery.domain.model.enums.DeliveryUrgency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Lot L1 (H6): the gofp urgency is the core's; the legacy NORMAL is still accepted on input as STANDARD. */
class DeliveryUrgencyInputDeserializerTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"NORMAL,STANDARD", "normal,STANDARD", "STANDARD,STANDARD", "express,EXPRESS",
            "SAME_DAY,SAME_DAY", "Scheduled,SCHEDULED"})
    @DisplayName("update request: legacy and core values, any case")
    void accepted(String sent, DeliveryUrgency expected) throws Exception {
        DeliveryUpdateDTO dto = json.readValue("{\"urgency\":\"" + sent + "\"}", DeliveryUpdateDTO.class);
        assertThat(dto.getUrgency()).isEqualTo(expected);
    }

    @Test
    @DisplayName("blank or absent: no urgency")
    void blank() throws Exception {
        assertThat(json.readValue("{\"urgency\":\"\"}", DeliveryUpdateDTO.class).getUrgency()).isNull();
        assertThat(json.readValue("{}", DeliveryUpdateDTO.class).getUrgency()).isNull();
    }

    @Test
    @DisplayName("an unknown value is a 400-class input error")
    void unknown() {
        assertThatThrownBy(() -> json.readValue("{\"urgency\":\"URGENT\"}", DeliveryUpdateDTO.class))
                .isInstanceOf(InvalidFormatException.class);
    }

    @Test
    @DisplayName("the response writes the core value (STANDARD, never NORMAL)")
    void responseWritesCoreValue() throws Exception {
        DeliveryResponseDTO dto = new DeliveryResponseDTO();
        dto.setUrgency(DeliveryUrgency.STANDARD);
        assertThat(json.writeValueAsString(dto)).contains("\"urgency\":\"STANDARD\"");
    }
}
