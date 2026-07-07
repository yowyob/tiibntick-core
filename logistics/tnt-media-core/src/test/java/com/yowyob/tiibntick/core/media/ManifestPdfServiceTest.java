package com.yowyob.tiibntick.core.media;

import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.PDFRenderRequest;
import com.yowyob.tiibntick.core.media.domain.PDFType;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import com.yowyob.tiibntick.core.media.port.outbound.IObjectStorageClient;
import com.yowyob.tiibntick.core.media.service.ManifestPdfService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ManifestPdfService}.
 * Verifies that PDF rendering produces a non-null, non-empty byte array.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
@Tag("integration")
class ManifestPdfServiceTest {

    @Mock
    private IObjectStorageClient storageClient;

    @Mock
    private IMediaRepository mediaRepository;

    private ManifestPdfService service;

    @BeforeEach
    void setUp() {
        service = new ManifestPdfService(storageClient, mediaRepository);
        when(storageClient.ensureBucketExists(anyString())).thenReturn(Mono.empty());
        when(storageClient.upload(anyString(), anyString(), any(byte[].class), anyString()))
                .thenReturn(Mono.just("etag-pdf-test"));
        when(mediaRepository.save(any(MediaFile.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void generateBytes_manifest_shouldProduceNonEmptyPdf() {
        PDFRenderRequest request = PDFRenderRequest.builder()
                .templateType(PDFType.MANIFEST)
                .tenantId("tenant-test")
                .locale("fr_CM")
                .variables(Map.of(
                        "trackingCode", "TNT-CMR-2026-000001",
                        "missionId", "mission-uuid-here",
                        "agencyName", "Agence TiiBnTick Yaoundé",
                        "shipperName", "Jean Dupont",
                        "recipientName", "Marie Ngono",
                        "deliveryAddress", "Rue de la Joie, Yaoundé, Cameroun",
                        "packageDescription", "Vêtements, 2kg",
                        "createdAt", "16/05/2026 10:00"))
                .outputFileName("test_manifest_001")
                .build();

        StepVerifier.create(service.generateBytes(request))
                .assertNext(bytes -> {
                    assertThat(bytes).isNotNull();
                    assertThat(bytes.length).isGreaterThan(0);
                    // PDF files start with %PDF
                    assertThat(new String(bytes, 0, 4)).isEqualTo("%PDF");
                })
                .verifyComplete();
    }

    @Test
    void generateBytes_hubReceipt_shouldProduceNonEmptyPdf() {
        PDFRenderRequest request = PDFRenderRequest.builder()
                .templateType(PDFType.HUB_DEPOSIT_RECEIPT)
                .tenantId("tenant-test")
                .locale("fr_CM")
                .variables(Map.of(
                        "depositId", "DEP-00001",
                        "trackingCode", "TNT-CMR-2026-000002",
                        "hubName", "Point Relais Marché Central",
                        "shipperName", "Paul Biya",
                        "packageDescription", "Électronique",
                        "depositTime", "16/05/2026 09:30",
                        "generatedAt", "16/05/2026 09:31"))
                .build();

        StepVerifier.create(service.generateBytes(request))
                .assertNext(bytes -> {
                    assertThat(bytes).isNotNull();
                    assertThat(bytes.length).isGreaterThan(0);
                })
                .verifyComplete();
    }

    @Test
    void generateAndStore_shouldReturnStorageKey() {
        PDFRenderRequest request = PDFRenderRequest.builder()
                .templateType(PDFType.INVOICE)
                .tenantId("tenant-test")
                .locale("fr_CM")
                .variables(Map.ofEntries(
                        Map.entry("invoiceNumber", "TNT-FACT-test-2026-0001"),
                        Map.entry("invoiceDate", "16/05/2026"),
                        Map.entry("tenantName", "Agence TiiBnTick"),
                        Map.entry("clientName", "Client Test"),
                        Map.entry("clientAddress", "Yaoundé, Cameroun"),
                        Map.entry("trackingCode", "TNT-CMR-2026-000003"),
                        Map.entry("serviceLine", "Livraison Express"),
                        Map.entry("amountHT", "2 000"),
                        Map.entry("tvaRate", "19.25%"),
                        Map.entry("tvaAmount", "385"),
                        Map.entry("totalTTC", "2 385"),
                        Map.entry("currency", "XAF"),
                        Map.entry("paymentMethod", "MTN Mobile Money")))
                .outputFileName("facture_test_0001")
                .build();

        StepVerifier.create(service.generateAndStore(request))
                .assertNext(key -> assertThat(key)
                        .isNotBlank()
                        .startsWith("documents/invoice/")
                        .endsWith(".pdf"))
                .verifyComplete();
    }
}
