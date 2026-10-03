package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.consumer.MatchingNotificationConsumer;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.kafka.event.MatchingNotificationEvent;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.notification.NotificationStreamAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.test.web.reactive.server.FluxExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lot C-21.2 — byte-for-byte contract of the SSE stream {@code GET /api/notifications/stream/{id}},
 * consumed by the mobile app.
 *
 * <p>Written and run <em>before</em> the controller stopped exposing the Kafka event
 * {@link MatchingNotificationEvent} in its signature, then run unchanged after: the event goes
 * in through the real Kafka consumer method and the real stream adapter (both signatures are
 * the same on either side of the refactor), and the raw response bytes are compared to a
 * golden string. Two codec setups: the one {@code TntWebFluxConfig} installs in the app
 * (Jackson 2, field visibility, getters hidden), and Spring's defaults.
 */
class NotificationStreamControllerSseContractTest {

    static final UUID FREELANCER_ID = UUID.fromString("c21c21c2-0000-0000-0000-00000000f001");
    static final UUID ANNOUNCEMENT_ID = UUID.fromString("c21c21c2-0000-0000-0000-00000000a001");

    /** {@code message} deliberately null: how nulls are written is part of the contract. */
    static final String GOLDEN = ":connected\n\n"
            + "event:notification\n"
            + "data:{\"freelancerId\":\"c21c21c2-0000-0000-0000-00000000f001\","
            + "\"announcementId\":\"c21c21c2-0000-0000-0000-00000000a001\","
            + "\"title\":\"Annonce C-21 été\",\"message\":null}\n\n";

    @Test
    void sseBytes_withAppCodecs_matchGolden() {
        assertThat(streamOneEvent(true)).isEqualTo(GOLDEN);
    }

    @Test
    void sseBytes_withSpringDefaultCodecs_matchGolden() {
        assertThat(streamOneEvent(false)).isEqualTo(GOLDEN);
    }

    private static String streamOneEvent(boolean appCodecs) {
        NotificationStreamAdapter stream = new NotificationStreamAdapter();
        MatchingNotificationConsumer consumer = new MatchingNotificationConsumer(stream);
        NotificationStreamController controller = new NotificationStreamController(stream);

        WebTestClient.ControllerSpec spec = WebTestClient.bindToController(controller);
        if (appCodecs) {
            // Same setup as TntWebFluxConfig#configureHttpMessageCodecs / #tntJsonMapper.
            JsonMapper mapper = JsonMapper.builder()
                    .findAndAddModules()
                    .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                    .visibility(PropertyAccessor.FIELD, Visibility.ANY)
                    .visibility(PropertyAccessor.GETTER, Visibility.NONE)
                    .visibility(PropertyAccessor.IS_GETTER, Visibility.NONE)
                    .build();
            spec.httpMessageCodecs(c -> {
                c.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper));
                c.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper));
            });
        }
        WebTestClient client = spec.build();

        FluxExchangeResult<DataBuffer> result = client.get()
                .uri("/api/notifications/stream/{id}", FREELANCER_ID)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(DataBuffer.class);

        consumer.consumeMatchingNotification(MatchingNotificationEvent.builder()
                .freelancerId(FREELANCER_ID)
                .announcementId(ANNOUNCEMENT_ID)
                .title("Annonce C-21 été")
                .message(null)
                .build());

        return result.getResponseBody()
                .map(buffer -> {
                    String chunk = buffer.toString(StandardCharsets.UTF_8);
                    DataBufferUtils.release(buffer);
                    return chunk;
                })
                .take(Duration.ofSeconds(2))
                .reduce("", String::concat)
                .block(Duration.ofSeconds(5));
    }
}
