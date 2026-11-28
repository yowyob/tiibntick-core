package com.yowyob.tiibntick.core.administration.adapter.in.web.dto;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yowyob.tiibntick.core.administration.adapter.in.web.dto.request.UpdateTntPlatformOptionsRequest;
import com.yowyob.tiibntick.core.administration.adapter.in.web.dto.response.TntPlatformOptionsResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Platform options — relayPointMaxStorageHours removed without breaking old clients")
class UpdateTntPlatformOptionsRequestCompatibilityTest {

    // unknown properties fail, as with the application's WebFlux mapper (Jackson default)
    private final JsonMapper strict = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();

    private static final String BODY = "{\"blockchainEnabled\":false,\"freelancerModeEnabled\":true,"
            + "\"maxFreelancerConcurrentMissions\":3,\"pointRelaisModeEnabled\":true,"
            + "\"maxCourierAnnouncementResponses\":5,\"defaultCurrency\":\"XAF\",\"disputeFilingWindowDays\":7%s}";

    @Test
    @DisplayName("an old client still sending relayPointMaxStorageHours is accepted, the field is ignored")
    void oldClientTolerated() throws Exception {
        UpdateTntPlatformOptionsRequest request = strict.readValue(
                BODY.formatted(",\"relayPointMaxStorageHours\":72"), UpdateTntPlatformOptionsRequest.class);
        assertThat(request.defaultCurrency()).isEqualTo("XAF");
        assertThat(request.maxFreelancerConcurrentMissions()).isEqualTo(3);
    }

    @Test
    @DisplayName("other unknown fields are still refused; the field is gone from request and response")
    void fieldRemoved() {
        assertThatThrownBy(() -> strict.readValue(BODY.formatted(",\"somethingElse\":1"),
                UpdateTntPlatformOptionsRequest.class)).isInstanceOf(UnrecognizedPropertyException.class);
        assertThat(Arrays.stream(UpdateTntPlatformOptionsRequest.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("relayPointMaxStorageHours");
        assertThat(Arrays.stream(TntPlatformOptionsResponse.class.getDeclaredFields()).map(f -> f.getName()))
                .doesNotContain("relayPointMaxStorageHours");
    }
}
