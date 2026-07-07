package com.yowyob.tiibntick.core.media;

import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.MediaFileId;
import com.yowyob.tiibntick.core.media.domain.MediaType;
import com.yowyob.tiibntick.core.media.port.outbound.IMediaRepository;
import com.yowyob.tiibntick.core.media.port.outbound.IObjectStorageClient;
import com.yowyob.tiibntick.core.media.service.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StorageService}.
 *
 * @author MANFOUO Braun
 */
@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private IObjectStorageClient storageClient;

    @Mock
    private IMediaRepository mediaRepository;

    private StorageService service;

    @BeforeEach
    void setUp() {
        service = new StorageService(storageClient, mediaRepository);
    }

    @Test
    void upload_shouldReturnPersistedMediaFile() {
        byte[] data = "test-file-content".getBytes(StandardCharsets.UTF_8);

        when(mediaRepository.findByHashAndTenant(anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(storageClient.ensureBucketExists(anyString())).thenReturn(Mono.empty());
        when(storageClient.upload(anyString(), anyString(), any(byte[].class), anyString()))
                .thenReturn(Mono.just("etag-abc"));
        when(mediaRepository.save(any(MediaFile.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.upload(
                        "tenant-01", "user-01", MediaType.PROFILE_PHOTO,
                        "image/jpeg", "photo.jpg", data, false))
                .assertNext(file -> {
                    assertThat(file.getTenantId()).isEqualTo("tenant-01");
                    assertThat(file.getOwnerUserId()).isEqualTo("user-01");
                    assertThat(file.getType()).isEqualTo(MediaType.PROFILE_PHOTO);
                    assertThat(file.getSizeBytes()).isEqualTo(data.length);
                    assertThat(file.getStorageBucket()).isEqualTo("tnt-tenant-01");
                })
                .verifyComplete();
    }

    @Test
    void upload_shouldDeduplicateByHash() {
        byte[] data = "duplicate-content".getBytes(StandardCharsets.UTF_8);

        MediaFile existing = MediaFile.create(
                "tenant-01", null, MediaType.KYC_DOCUMENT,
                "application/pdf", "kyc.pdf",
                "tnt-tenant-01", "kyc/existing.pdf",
                data.length, "hash-abc", false, null, java.util.Collections.emptyMap());

        when(mediaRepository.findByHashAndTenant(anyString(), anyString()))
                .thenReturn(Mono.just(existing));

        StepVerifier.create(service.upload(
                        "tenant-01", "user-01", MediaType.KYC_DOCUMENT,
                        "application/pdf", "kyc_copy.pdf", data, false))
                .assertNext(file -> {
                    assertThat(file.getStorageKey()).isEqualTo("kyc/existing.pdf");
                })
                .verifyComplete();

        // Verify no new upload to MinIO for duplicate
        verify(storageClient, times(0)).upload(anyString(), anyString(), any(), anyString());
    }

    @Test
    void delete_shouldRemoveFromStorageAndMetadata() {
        MediaFileId fileId = MediaFileId.generate();
        MediaFile existing = MediaFile.create(
                "tenant-01", null, MediaType.SIGNATURE,
                "image/png", "sig.png",
                "tnt-tenant-01", "signatures/sig_123.png",
                512L, "hash-sig", false, null, java.util.Collections.emptyMap());

        when(mediaRepository.findById(fileId)).thenReturn(Mono.just(existing));
        when(storageClient.delete(anyString(), anyString())).thenReturn(Mono.empty());
        when(mediaRepository.deleteById(fileId)).thenReturn(Mono.empty());

        StepVerifier.create(service.delete(fileId, "tenant-01"))
                .verifyComplete();

        verify(storageClient, times(1)).delete("tnt-tenant-01", "signatures/sig_123.png");
        verify(mediaRepository, times(1)).deleteById(fileId);
    }
}
