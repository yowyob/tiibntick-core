package com.yowyob.tiibntick.core.actor.adapter.out.kernel;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The real HTTP shape of the Kernel calls behind the profile photo, against a local server. */
class KernelUserAccountAdapterTest {

    private MockWebServer server;
    private KernelUserAccountAdapter adapter;
    private final UUID fileId = UUID.randomUUID();

    @BeforeEach
    void start() throws Exception {
        server = new MockWebServer();
        server.start();
        adapter = new KernelUserAccountAdapter(WebClient.builder().baseUrl(server.url("/").toString()).build());
    }

    @AfterEach
    void stop() throws Exception {
        server.shutdown();
    }

    private static MockResponse json(String body) {
        return new MockResponse().setResponseCode(200).addHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void readsTheAccountWithItsAvatar() throws Exception {
        server.enqueue(json("{\"success\":true,\"data\":{\"id\":\"" + UUID.randomUUID() + "\",\"firstName\":\"Jo\","
                + "\"lastName\":\"Mballa\",\"email\":\"jo@x.cm\",\"avatarId\":\"" + fileId + "\",\"unknown\":1}}"));
        StepVerifier.create(adapter.findCurrent())
                .assertNext(a -> {
                    assertThat(a.avatarId()).isEqualTo(fileId);
                    assertThat(a.email()).isEqualTo("jo@x.cm");
                    assertThat(a.displayName()).isEqualTo("Jo Mballa");
                })
                .verifyComplete();
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/users/me");
    }

    @Test
    void setsAndClearsTheAvatarThroughTheAvatarEndpoint() throws Exception {
        server.enqueue(json("{\"success\":true,\"data\":{\"avatarId\":\"" + fileId + "\"}}"));
        server.enqueue(json("{\"success\":true,\"data\":{}}"));
        StepVerifier.create(adapter.setAvatar(fileId)).expectNextCount(1).verifyComplete();
        StepVerifier.create(adapter.setAvatar(null)).expectNextCount(1).verifyComplete();

        RecordedRequest set = server.takeRequest();
        assertThat(set.getMethod()).isEqualTo("PUT");
        assertThat(set.getPath()).isEqualTo("/api/users/me/avatar");
        assertThat(set.getBody().readUtf8()).contains(fileId.toString());
        assertThat(server.takeRequest().getBody().readUtf8()).contains("\"avatarId\":null");
    }

    @Test
    void mirrorsThePhotoOnTheActorIdentity() throws Exception {
        server.enqueue(json("{\"success\":true,\"data\":{}}"));
        StepVerifier.create(adapter.setActorPhoto(fileId)).verifyComplete();
        RecordedRequest r = server.takeRequest();
        assertThat(r.getPath()).isEqualTo("/api/actors/me/identity");
        assertThat(r.getBody().readUtf8()).contains("\"photoId\":\"" + fileId + "\"");
    }

    @Test
    void streamsTheFileContentWithItsType() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).addHeader("Content-Type", "image/png").setBody("PNGDATA"));
        StepVerifier.create(adapter.fileContent(fileId)
                        .flatMap(c -> c.body().map(b -> {
                            byte[] bytes = new byte[b.readableByteCount()];
                            b.read(bytes);
                            return c.contentType() + ":" + new String(bytes);
                        }).collectList()))
                .assertNext(l -> assertThat(String.join("", l)).isEqualTo("image/png:PNGDATA"))
                .verifyComplete();
        assertThat(server.takeRequest().getPath()).isEqualTo("/api/files/" + fileId + "/content");
    }

    @Test
    void aMissingFileIsEmptyNotAnError() {
        server.enqueue(new MockResponse().setResponseCode(404));
        StepVerifier.create(adapter.fileContent(fileId)).verifyComplete();
    }
}
