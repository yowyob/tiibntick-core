package com.yowyob.tiibntick.core.roles.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yowyob.tiibntick.core.roles.application.port.out.RoleSyncOutboxRepository;
import com.yowyob.tiibntick.core.roles.application.port.out.UserRoleAssignmentRepository;
import com.yowyob.tiibntick.core.roles.domain.exception.TntRoleException;
import com.yowyob.tiibntick.core.roles.domain.model.RoleScopeType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncAggregateType;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOperation;
import com.yowyob.tiibntick.core.roles.domain.model.RoleSyncOutboxEntry;
import com.yowyob.tiibntick.core.roles.domain.model.UserRoleAssignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TntRoleRevocationService}.
 */
@ExtendWith(MockitoExtension.class)
class TntRoleRevocationServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();

    @Mock
    private UserRoleAssignmentRepository assignmentRepository;
    @Mock
    private RoleSyncOutboxRepository outboxRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private TntRoleRevocationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new TntRoleRevocationService(assignmentRepository, outboxRepository, transactionalOperator, objectMapper);
        lenient().when(transactionalOperator.transactional(any(Mono.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void revokeAssignment_neverSyncedToKernel_shouldDeleteLocalRowAndNeverTouchOutbox() {
        UserRoleAssignment assignment = UserRoleAssignment.assign(
                TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), RoleScopeType.AGENCY, UUID.randomUUID());
        when(assignmentRepository.findById(TENANT_ID, ASSIGNMENT_ID)).thenReturn(Mono.just(assignment));
        when(assignmentRepository.findKernelAssignmentId(TENANT_ID, ASSIGNMENT_ID)).thenReturn(Mono.empty());
        when(assignmentRepository.deleteById(TENANT_ID, ASSIGNMENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.revokeAssignment(TENANT_ID, ASSIGNMENT_ID))
                .verifyComplete();

        verify(assignmentRepository).deleteById(TENANT_ID, ASSIGNMENT_ID);
        verify(transactionalOperator).transactional(any(Mono.class));
        verifyNoInteractions(outboxRepository);
    }

    @Test
    void revokeAssignment_alreadySyncedToKernel_shouldEnqueueRevokeAssignmentOutboxEntryThenDelete() throws Exception {
        UUID kernelAssignmentId = UUID.randomUUID();
        UserRoleAssignment assignment = UserRoleAssignment.assign(
                TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), RoleScopeType.AGENCY, UUID.randomUUID());
        when(assignmentRepository.findById(TENANT_ID, ASSIGNMENT_ID)).thenReturn(Mono.just(assignment));
        when(assignmentRepository.findKernelAssignmentId(TENANT_ID, ASSIGNMENT_ID)).thenReturn(Mono.just(kernelAssignmentId));

        ArgumentCaptor<RoleSyncOutboxEntry> outboxCaptor = ArgumentCaptor.forClass(RoleSyncOutboxEntry.class);
        when(outboxRepository.save(outboxCaptor.capture())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(assignmentRepository.deleteById(TENANT_ID, ASSIGNMENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.revokeAssignment(TENANT_ID, ASSIGNMENT_ID))
                .verifyComplete();

        verify(assignmentRepository).deleteById(TENANT_ID, ASSIGNMENT_ID);

        RoleSyncOutboxEntry outboxEntry = outboxCaptor.getValue();
        assertThat(outboxEntry.operation()).isEqualTo(RoleSyncOperation.REVOKE_ASSIGNMENT);
        assertThat(outboxEntry.aggregateType()).isEqualTo(RoleSyncAggregateType.ASSIGNMENT);
        assertThat(outboxEntry.aggregateId()).isEqualTo(ASSIGNMENT_ID);
        assertThat(outboxEntry.tenantId()).isEqualTo(TENANT_ID);

        JsonNode payload = objectMapper.readTree(outboxEntry.payload());
        assertThat(payload.get("kernelAssignmentId").asText()).isEqualTo(kernelAssignmentId.toString());
    }

    @Test
    void revokeAssignment_unknownAssignment_shouldErrorAndNeverDelete() {
        when(assignmentRepository.findById(TENANT_ID, ASSIGNMENT_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.revokeAssignment(TENANT_ID, ASSIGNMENT_ID))
                .expectErrorSatisfies(err -> {
                    assertThat(err).isInstanceOf(TntRoleException.class);
                    assertThat(((TntRoleException) err).getCode()).isEqualTo("ROLE_ASSIGNMENT_NOT_FOUND");
                })
                .verify();

        verify(assignmentRepository, never()).deleteById(any(), any());
        verifyNoInteractions(outboxRepository);
    }
}
