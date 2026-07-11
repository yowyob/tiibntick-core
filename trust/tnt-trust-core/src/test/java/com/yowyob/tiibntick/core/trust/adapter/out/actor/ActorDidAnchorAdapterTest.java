package com.yowyob.tiibntick.core.trust.adapter.out.actor;

import com.yowyob.tiibntick.core.actor.application.port.out.ActorDidAnchorPayload;
import com.yowyob.tiibntick.core.trust.application.port.in.IssueDIDUseCase;
import com.yowyob.tiibntick.core.trust.domain.model.valueobject.DIDDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ActorDidAnchorAdapter}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ActorDidAnchorAdapter — IActorDidAnchorPort implementation")
class ActorDidAnchorAdapterTest {

    @Mock
    private IssueDIDUseCase issueDIDUseCase;

    @Test
    @DisplayName("should delegate to IssueDIDUseCase.issue with the payload's identifiers and return the DID")
    void shouldDelegateToIssueDIDUseCase() {
        final ActorDidAnchorAdapter adapter = new ActorDidAnchorAdapter(issueDIDUseCase);

        final UUID tenantId = UUID.randomUUID();
        final UUID actorId = UUID.randomUUID();
        final ActorDidAnchorPayload payload = new ActorDidAnchorPayload(tenantId, actorId);
        final DIDDocument document = DIDDocument.issue(
                actorId.toString(), tenantId.toString(), "pem-key", "https://api.tiibntick.com/actors/x/identity");

        when(issueDIDUseCase.issue(anyString(), anyString(), anyString())).thenReturn(Mono.just(document));

        StepVerifier.create(adapter.issueDid(payload))
                .expectNext(document.getDid())
                .verifyComplete();

        final ArgumentCaptor<String> actorIdCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<String> tenantIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(issueDIDUseCase).issue(actorIdCaptor.capture(), tenantIdCaptor.capture(), anyString());

        assertThat(actorIdCaptor.getValue()).isEqualTo(actorId.toString());
        assertThat(tenantIdCaptor.getValue()).isEqualTo(tenantId.toString());
    }
}
