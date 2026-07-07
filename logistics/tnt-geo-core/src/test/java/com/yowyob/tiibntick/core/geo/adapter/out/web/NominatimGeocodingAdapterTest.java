package com.yowyob.tiibntick.core.geo.adapter.out.web;

import com.yowyob.tiibntick.core.geo.domain.model.GeoPoint;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for NominatimGeocodingAdapter using MockWebServer to avoid live HTTP calls.
 *
 * Author: MANFOUO Braun
 */
class NominatimGeocodingAdapterTest {

    private MockWebServer mockWebServer;
    private NominatimGeocodingAdapter adapter;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        WebClient webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build();
        adapter = new NominatimGeocodingAdapter(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void geocode_validResponse_returnsAddressResult() {
        String responseBody = """
                [
                  {
                    "lat": "3.848",
                    "lon": "11.502",
                    "display_name": "Yaoundé, Centre Region, Cameroon",
                    "importance": 0.8
                  }
                ]
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        StepVerifier.create(adapter.geocode("Yaoundé", "YDE"))
                .assertNext(result -> {
                    assertThat(result.coordinates().latitude()).isCloseTo(3.848, org.assertj.core.data.Offset.offset(0.001));
                    assertThat(result.coordinates().longitude()).isCloseTo(11.502, org.assertj.core.data.Offset.offset(0.001));
                    assertThat(result.confidence()).isCloseTo(0.8, org.assertj.core.data.Offset.offset(0.01));
                })
                .verifyComplete();
    }

    @Test
    void geocode_emptyResponse_emitsError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody("[]"));

        StepVerifier.create(adapter.geocode("NonExistentPlace", "XXX"))
                .expectError()
                .verify();
    }

    @Test
    void reverseGeocode_validResponse_returnsAddressResult() {
        String responseBody = """
                {
                  "display_name": "Rue des Palmiers, Yaoundé, Cameroon",
                  "address": {
                    "city": "Yaoundé",
                    "country": "Cameroon"
                  }
                }
                """;
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .addHeader("Content-Type", "application/json")
                .setBody(responseBody));

        StepVerifier.create(adapter.reverseGeocode(GeoPoint.of(3.848, 11.502)))
                .assertNext(result -> {
                    assertThat(result.displayName()).contains("Yaoundé");
                    assertThat(result.confidence()).isEqualTo(0.9);
                })
                .verifyComplete();
    }
}
