package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.billing.wallet.application.port.in.IWalletUseCase;
import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.AnnouncementResponseDTO;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.AnnouncementSubscriptionRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpUserRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IAnnouncementRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryAnnouncementPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.INegotiationChatPort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementSnapshot;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpDeliveryOtpProperties;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.Delivery;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.announcement.AnnouncementStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Proves the deliveryOtp PREVIEW_ONLY mechanism:
 *   preview=false → deliveryConfirmationCode absent from the response DTO ;
 *   preview=true  → deliveryConfirmationCode present and equals the generated delivery OTP.
 *
 * The BCrypt hash in the DB is not affected by this test: the hash is set via
 * {@code DeliveryOtpService.hashOtp()} and {@code OtpService.verifyOtp()} is the sole
 * verification path — this test only covers the HTTP response DTO.
 */
@ExtendWith(MockitoExtension.class)
class DeliveryOtpPreviewModeTest {

    @Mock private IDeliveryAnnouncementPort deliveryAnnouncementPort;
    @Mock private TenantContextHolder tenantContextHolder;
    @Mock private FreelancerQuotaService freelancerQuotaService;
    @Mock private IWalletUseCase walletUseCase;
    @Mock private INegotiationChatPort negotiationChatPort;
    @Mock private IAnnouncementRepository announcementRepository;
    @Mock private AnnouncementSubscriptionRepository subscriptionRepository;
    @Mock private GofpFreelancerRepository gofpFreelancerRepository;
    @Mock private GofpUserRepository gofpUserRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private DeliveryOtpService deliveryOtpService;

    private final UUID tenantId       = UUID.randomUUID();
    private final UUID clientId       = UUID.randomUUID();
    private final UUID announcementId = UUID.randomUUID();
    private final UUID freelancerId   = UUID.randomUUID();
    private final UUID deliveryId     = UUID.randomUUID();

    private final String DELIVERY_OTP = "987654";
    private final String PICKUP_OTP   = "123456";

    @BeforeEach
    void setUp() {
        lenient().when(announcementRepository.findById(any())).thenReturn(Mono.empty());
        lenient().when(deliveryRepository.findById(any())).thenReturn(Mono.empty());
        lenient().when(gofpFreelancerRepository.findById(any())).thenReturn(Mono.empty());
        lenient().when(gofpFreelancerRepository.findByCoreFreelancerId(any())).thenReturn(Mono.empty());
    }

    @Test
    void assignResponse_previewFalse_deliveryConfirmationCodeAbsent() {
        GofpDeliveryOtpProperties props = new GofpDeliveryOtpProperties();
        props.setPreviewMode(false);

        AnnouncementApplicationService svc = buildService(props);
        stubAssignPath(svc);

        StepVerifier.create(svc.assignResponse(announcementId, clientId, responseId()))
                .assertNext(dto -> {
                    assertThat(dto.getConfirmationCode()).isEqualTo(PICKUP_OTP);
                    assertThat(dto.getDeliveryConfirmationCode())
                            .as("deliveryConfirmationCode must be absent when preview=false")
                            .isNull();
                    assertThat(dto.getDeliveryOtpDeliveryMode()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void assignResponse_previewTrue_deliveryConfirmationCodePresent() {
        GofpDeliveryOtpProperties props = new GofpDeliveryOtpProperties();
        props.setPreviewMode(true);

        AnnouncementApplicationService svc = buildService(props);
        stubAssignPath(svc);

        StepVerifier.create(svc.assignResponse(announcementId, clientId, responseId()))
                .assertNext(dto -> {
                    assertThat(dto.getConfirmationCode()).isEqualTo(PICKUP_OTP);
                    assertThat(dto.getDeliveryConfirmationCode())
                            .as("deliveryConfirmationCode must equal the generated delivery OTP when preview=true")
                            .isEqualTo(DELIVERY_OTP);
                    assertThat(dto.getDeliveryOtpDeliveryMode()).isEqualTo("PREVIEW_ONLY");
                })
                .verifyComplete();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private UUID responseId() {
        return UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    }

    private AnnouncementApplicationService buildService(GofpDeliveryOtpProperties props) {
        return new AnnouncementApplicationService(
                deliveryAnnouncementPort, tenantContextHolder, freelancerQuotaService,
                walletUseCase, negotiationChatPort, announcementRepository, subscriptionRepository,
                gofpFreelancerRepository, gofpUserRepository, deliveryRepository, deliveryOtpService,
                props);
    }

    private void stubAssignPath(AnnouncementApplicationService svc) {
        UUID respId = responseId();
        var response = new com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot(
                respId, freelancerId, BigDecimal.valueOf(3000), "XAF", "SENT", Instant.now());
        AnnouncementSnapshot before = snapshot(List.of(response), null);
        AnnouncementSnapshot assigned = snapshot(List.of(response), deliveryId);

        Delivery delivery = new Delivery();
        delivery.setId(deliveryId);
        OtpInitResult otp = new OtpInitResult(delivery, PICKUP_OTP, DELIVERY_OTP, true);

        when(tenantContextHolder.currentTenantId()).thenReturn(Mono.just(tenantId));
        when(deliveryAnnouncementPort.findById(tenantId, announcementId)).thenReturn(Mono.just(before));
        lenient().when(deliveryAnnouncementPort.resolveEscrowAmount(any(), any(), any()))
                .thenReturn(Mono.just(BigDecimal.ZERO));
        when(deliveryAnnouncementPort.selectResponse(tenantId, announcementId, clientId, respId))
                .thenReturn(Mono.just(assigned));
        when(announcementRepository.findById(announcementId)).thenReturn(Mono.empty());
        when(deliveryRepository.findById(deliveryId)).thenReturn(Mono.just(delivery));
        when(deliveryOtpService.initOtpIfAbsentWithCodes(any(Delivery.class))).thenReturn(Mono.just(otp));
    }

    private AnnouncementSnapshot snapshot(
            List<com.yowyob.tiibntick.core.gofreelancer.application.port.out.dto.AnnouncementResponseSnapshot> responses,
            UUID createdDeliveryId) {
        return new AnnouncementSnapshot(
                announcementId, tenantId, clientId, "Test", "desc",
                AnnouncementStatus.PUBLISHED, Instant.now(), Instant.now(),
                null, "XAF", AnnouncementSnapshot.PRICING_MODE_FIXED_PRICE,
                null, null, 0.0,
                null, null, null, null, null, null, null, null,
                "Recipient", "+237600000000",
                responses.isEmpty() ? null : responses.get(0).id(),
                createdDeliveryId, null, responses);
    }
}
