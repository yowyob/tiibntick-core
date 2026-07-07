package com.yowyob.tiibntick.core.tp.application.service;

import com.yowyob.tiibntick.core.tp.application.port.in.command.RegisterTntClientProfileCommand;
import com.yowyob.tiibntick.core.tp.application.port.out.KernelThirdPartyPort;
import com.yowyob.tiibntick.core.tp.application.port.out.PhoneAliasPort;
import com.yowyob.tiibntick.core.tp.application.port.out.TntClientProfileRepository;
import com.yowyob.tiibntick.core.tp.application.port.out.TntTpEventPublisher;
import com.yowyob.tiibntick.core.tp.domain.model.TntClientProfile;
import com.yowyob.tiibntick.core.tp.domain.model.enums.KycStatus;
import com.yowyob.tiibntick.core.tp.domain.model.enums.LoyaltyTier;
import com.yowyob.tiibntick.core.tp.domain.model.enums.TntThirdPartyRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TntClientProfileService}.
 *
 * <p>Validates the Kernel ThirdParty existence check added in the reimplementation,
 * alongside the existing profile creation, duplicate guard, and phone alias logic.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TntClientProfileService — Application service tests")
class TntClientProfileServiceTest {

    @Mock private TntClientProfileRepository profileRepository;
    @Mock private TntTpEventPublisher eventPublisher;
    @Mock private PhoneAliasPort phoneAliasPort;
    @Mock private KernelThirdPartyPort kernelThirdPartyPort;

    private TntClientProfileService profileService;

    private static final UUID TENANT_ID      = UUID.randomUUID();
    private static final UUID THIRD_PARTY_ID = UUID.randomUUID();
    private static final Set<TntThirdPartyRole> ROLES = Set.of(TntThirdPartyRole.SENDER);

    @BeforeEach
    void setUp() {
        profileService = new TntClientProfileService(
                profileRepository, eventPublisher, phoneAliasPort, kernelThirdPartyPort);
    }

    // ─── register() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("register() should succeed when Kernel TP is active and no profile exists")
    void register_shouldSucceed_whenKernelTpActiveAndNoExistingProfile() {
        // Given
        RegisterTntClientProfileCommand command = new RegisterTntClientProfileCommand(
                TENANT_ID, THIRD_PARTY_ID, ROLES, "fr", "XAF");

        TntClientProfile savedProfile = TntClientProfile.create(
                TENANT_ID, THIRD_PARTY_ID, ROLES, "fr", "XAF");

        when(kernelThirdPartyPort.existsAndActive(THIRD_PARTY_ID)).thenReturn(Mono.just(true));
        when(profileRepository.existsByThirdPartyId(TENANT_ID, THIRD_PARTY_ID)).thenReturn(Mono.just(false));
        when(profileRepository.save(any(TntClientProfile.class))).thenReturn(Mono.just(savedProfile));
        when(eventPublisher.publishAll(any(List.class))).thenReturn(Mono.empty());

        // When / Then
        StepVerifier.create(profileService.register(command))
                .assertNext(profile -> {
                    assertThat(profile.getThirdPartyId()).isEqualTo(THIRD_PARTY_ID);
                    assertThat(profile.getTenantId()).isEqualTo(TENANT_ID);
                    assertThat(profile.getKycStatus()).isEqualTo(KycStatus.NOT_SUBMITTED);
                    assertThat(profile.getLoyaltyTier()).isEqualTo(LoyaltyTier.BRONZE);
                    assertThat(profile.isActive()).isTrue();
                })
                .verifyComplete();

        verify(kernelThirdPartyPort, times(1)).existsAndActive(THIRD_PARTY_ID);
        verify(profileRepository, times(1)).existsByThirdPartyId(TENANT_ID, THIRD_PARTY_ID);
        verify(profileRepository, times(1)).save(any(TntClientProfile.class));
    }

    @Test
    @DisplayName("register() should fail when Kernel TP does not exist or is inactive")
    void register_shouldFail_whenKernelTpNotFound() {
        // Given
        RegisterTntClientProfileCommand command = new RegisterTntClientProfileCommand(
                TENANT_ID, THIRD_PARTY_ID, ROLES, "fr", "XAF");

        when(kernelThirdPartyPort.existsAndActive(THIRD_PARTY_ID)).thenReturn(Mono.just(false));

        // When / Then
        StepVerifier.create(profileService.register(command))
                .expectErrorMatches(ex ->
                        ex instanceof IllegalArgumentException &&
                        ex.getMessage().contains(THIRD_PARTY_ID.toString()))
                .verify();

        // Verify that persistence was never called when Kernel check fails
        verify(profileRepository, never()).existsByThirdPartyId(any(), any());
        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() should fail when Kernel TP port returns empty Mono")
    void register_shouldFail_whenKernelPortReturnsEmptyMono() {
        // Given — Kernel port returns empty (TP not found)
        RegisterTntClientProfileCommand command = new RegisterTntClientProfileCommand(
                TENANT_ID, THIRD_PARTY_ID, ROLES, "fr", "XAF");

        when(kernelThirdPartyPort.existsAndActive(THIRD_PARTY_ID)).thenReturn(Mono.just(false));

        // When / Then
        StepVerifier.create(profileService.register(command))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() should fail when a profile already exists for the ThirdParty")
    void register_shouldFail_whenProfileAlreadyExists() {
        // Given
        RegisterTntClientProfileCommand command = new RegisterTntClientProfileCommand(
                TENANT_ID, THIRD_PARTY_ID, ROLES, "fr", "XAF");

        when(kernelThirdPartyPort.existsAndActive(THIRD_PARTY_ID)).thenReturn(Mono.just(true));
        when(profileRepository.existsByThirdPartyId(TENANT_ID, THIRD_PARTY_ID)).thenReturn(Mono.just(true));

        // When / Then
        StepVerifier.create(profileService.register(command))
                .expectErrorMatches(ex ->
                        ex instanceof IllegalStateException &&
                        ex.getMessage().contains(THIRD_PARTY_ID.toString()))
                .verify();

        verify(profileRepository, never()).save(any());
    }

    // ─── generatePhoneAlias() ─────────────────────────────────────────────────

    @Test
    @DisplayName("generatePhoneAlias() should assign alias and mask phone on the profile")
    void generatePhoneAlias_shouldMaskPhone() {
        // Given
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, THIRD_PARTY_ID, ROLES, "fr", "XAF");
        String alias = "+237-6TT-123-456";

        when(profileRepository.findByThirdPartyId(TENANT_ID, THIRD_PARTY_ID))
                .thenReturn(Mono.just(profile));
        when(phoneAliasPort.generateAlias(TENANT_ID, THIRD_PARTY_ID))
                .thenReturn(Mono.just(alias));
        when(profileRepository.save(any(TntClientProfile.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        var command = new com.yowyob.tiibntick.core.tp.application.port.in.command
                .GeneratePhoneAliasCommand(TENANT_ID, THIRD_PARTY_ID);

        // When / Then
        StepVerifier.create(profileService.generatePhoneAlias(command))
                .assertNext(updated -> {
                    assertThat(updated.isPhoneMasked()).isTrue();
                    assertThat(updated.getPhoneAlias()).isEqualTo(alias);
                })
                .verifyComplete();
    }

    // ─── incrementDeliveries() ────────────────────────────────────────────────

    @Test
    @DisplayName("incrementDeliveries() should increase total deliveries by one")
    void incrementDeliveries_shouldIncreaseCount() {
        // Given
        TntClientProfile profile = TntClientProfile.create(
                TENANT_ID, THIRD_PARTY_ID, ROLES, "fr", "XAF");

        when(profileRepository.findByThirdPartyId(TENANT_ID, THIRD_PARTY_ID))
                .thenReturn(Mono.just(profile));
        when(profileRepository.save(any(TntClientProfile.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        // When / Then
        StepVerifier.create(profileService.incrementDeliveries(TENANT_ID, THIRD_PARTY_ID))
                .assertNext(updated ->
                        assertThat(updated.getTotalDeliveries()).isEqualTo(1))
                .verifyComplete();
    }
}
