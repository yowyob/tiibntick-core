package com.yowyob.tiibntick.core.sync.adapter.in.rest;

import com.yowyob.tiibntick.core.auth.domain.model.TntUserIdentity;
import com.yowyob.tiibntick.core.sync.application.port.in.IComputeDeltaUseCase;
import com.yowyob.tiibntick.core.sync.application.port.in.IProcessSyncBatchUseCase;
import com.yowyob.tiibntick.core.sync.application.port.out.IDuckDbSchemaProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** {@code X-User-Id} is client-supplied: it must never select whose data is pushed or pulled. */
class SyncControllerSelfCheckTest {

    private final IProcessSyncBatchUseCase push = mock(IProcessSyncBatchUseCase.class);
    private final IComputeDeltaUseCase delta = mock(IComputeDeltaUseCase.class);
    private final IDuckDbSchemaProvider schema = mock(IDuckDbSchemaProvider.class);
    private final SyncController controller = new SyncController(push, delta, schema);

    private static TntUserIdentity identity(UUID userId) {
        return new TntUserIdentity(userId, UUID.randomUUID(), UUID.randomUUID(), null, null, Set.of(), true);
    }

    private static void assertForbidden(Throwable t) {
        assertThat(t).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) t).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("pull with another user's id in X-User-Id is refused before any delta is computed")
    void pullForAnotherUserIsRefused() {
        TntUserIdentity me = identity(UUID.randomUUID());
        assertThatThrownBy(() -> controller.pullSync(UUID.randomUUID().toString(), me, "dev", null, null))
                .satisfies(SyncControllerSelfCheckTest::assertForbidden);
        verifyNoInteractions(delta);
    }

    @Test
    @DisplayName("bootstrap with another user's id is refused")
    void bootstrapForAnotherUserIsRefused() {
        TntUserIdentity me = identity(UUID.randomUUID());
        assertThatThrownBy(() -> controller.bootstrap(UUID.randomUUID().toString(), me, "dev", null))
                .satisfies(SyncControllerSelfCheckTest::assertForbidden);
        verifyNoInteractions(delta);
    }

    @Test
    @DisplayName("push as another user is refused before anything is written")
    void pushAsAnotherUserIsRefused() {
        TntUserIdentity me = identity(UUID.randomUUID());
        assertThatThrownBy(() -> controller.pushSync(UUID.randomUUID().toString(), me, "dev", null))
                .satisfies(SyncControllerSelfCheckTest::assertForbidden);
        verifyNoInteractions(push);
    }
}
