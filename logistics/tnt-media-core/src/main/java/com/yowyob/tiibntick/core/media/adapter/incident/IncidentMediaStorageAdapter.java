package com.yowyob.tiibntick.core.media.adapter.incident;

import com.yowyob.tiibntick.core.incident.port.outbound.IMediaStoragePort;
import com.yowyob.tiibntick.core.media.domain.MediaFile;
import com.yowyob.tiibntick.core.media.domain.exception.StorageException;
import com.yowyob.tiibntick.core.media.port.outbound.IObjectStorageClient;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Adapter implementing {@link IMediaStoragePort} (port defined in tnt-incident-core).
 *
 * <p>Manages the archival of incident evidence files from their upload buckets
 * into the dedicated {@value INCIDENT_EVIDENCES_BUCKET} archive bucket, which
 * is configured with WORM (Write Once Read Many) retention for legal compliance.</p>
 *
 * <h3>Bucket strategy:</h3>
 * <ul>
 *   <li><b>Upload stage</b>: evidence files are initially uploaded to the tenant's
 *       normal media bucket (e.g. {@code tnt-{tenantId}}) under the prefix
 *       {@code incident_evidence/}.</li>
 *   <li><b>Archive stage</b> (called by this adapter): files are copied to
 *       {@value INCIDENT_EVIDENCES_BUCKET} with the prefix
 *       {@code incidents/{incidentId}/evidences/} and the originals are deleted
 *       from the staging location.</li>
 *   <li><b>WORM policy</b>: the archive bucket has a MinIO Object Lock policy
 *       (GOVERNANCE mode, 7-year retention) to prevent tampering with evidence.</li>
 * </ul>
 *
 * <h3>Object key convention:</h3>
 * <pre>
 * Archive:  incidents/{incidentId}/evidences/{originalObjectKey}
 * Staging:  incident_evidence/{filename}  (in tenant bucket)
 * </pre>
 *
 * <p>Hexagonal position: secondary adapter in tnt-media-core implementing a port
 * from tnt-incident-core. Assembled in tnt-bootstrap.</p>
 *
 * @author MANFOUO Braun
 */
@Component
public class IncidentMediaStorageAdapter implements IMediaStoragePort, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(IncidentMediaStorageAdapter.class);

    /**
     * Dedicated MinIO bucket for incident evidence archives.
     * Must be configured with WORM / Object Lock retention in production.
     * Retention: 7 years (legal compliance requirement for logistics incidents in Cameroon).
     */
    public static final String INCIDENT_EVIDENCES_BUCKET = "tnt-incident-evidences";

    /** Staging prefix inside tenant buckets where evidence is first uploaded. */
    private static final String STAGING_PREFIX = "incident_evidence/";

    /** Archive prefix inside the evidence archive bucket. */
    private static final String ARCHIVE_PREFIX = "incidents/";

    private final MinioClient minioClient;
    private final IObjectStorageClient storageClient;

    public IncidentMediaStorageAdapter(MinioClient minioClient,
                                        IObjectStorageClient storageClient) {
        this.minioClient = minioClient;
        this.storageClient = storageClient;
    }

    /**
     * Ensures the {@value INCIDENT_EVIDENCES_BUCKET} archive bucket exists at startup.
     * Creates it if absent. In production, a separate Terraform/MinIO policy applies
     * the WORM retention configuration.
     */
    @Override
    public void afterPropertiesSet() {
        Mono.fromCallable(() -> {
                    boolean exists = minioClient.bucketExists(
                            BucketExistsArgs.builder().bucket(INCIDENT_EVIDENCES_BUCKET).build());
                    if (!exists) {
                        log.info("Creating MinIO archive bucket: {}", INCIDENT_EVIDENCES_BUCKET);
                        // Note: Object Lock must be enabled at bucket creation time
                        // via MinIO admin or Terraform in production.
                        // Here we create it without lock for test environments.
                        minioClient.makeBucket(
                                io.minio.MakeBucketArgs.builder()
                                        .bucket(INCIDENT_EVIDENCES_BUCKET)
                                        .build());
                        log.info("Archive bucket created: {} (configure WORM via MinIO admin for production)",
                                INCIDENT_EVIDENCES_BUCKET);
                    } else {
                        log.debug("Archive bucket already exists: {}", INCIDENT_EVIDENCES_BUCKET);
                    }
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(ex -> {
                    log.error("Failed to ensure archive bucket exists — incident evidence archival may fail: {}",
                            ex.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    /**
     * Archives all evidence files associated with a given incident.
     *
     * <p>For each staging object found under the tenant's evidence prefix for this incident,
     * the method:</p>
     * <ol>
     *   <li>Copies the object from the tenant bucket to {@value INCIDENT_EVIDENCES_BUCKET}
     *       under the archive prefix {@code incidents/{incidentId}/evidences/}.</li>
     *   <li>Deletes the original from the staging location.</li>
     * </ol>
     *
     * <p>If no staging files are found, the operation completes silently. Errors during
     * individual file moves are logged but do not abort the whole archival operation.</p>
     *
     * @param tenantId   UUID of the tenant that owns the staging bucket the evidence was
     *                   originally uploaded to (see {@link MediaFile#bucketNameFor(String)})
     * @param incidentId UUID of the incident whose evidence must be archived
     * @return Mono completing when all evidence files are archived
     */
    @Override
    public Mono<Void> archiveIncidentEvidence(UUID tenantId, UUID incidentId) {
        log.info("Archiving evidence for incidentId={} tenantId={}", incidentId, tenantId);

        String tenantBucket = MediaFile.bucketNameFor(tenantId.toString());
        // The evidence files are tagged with incidentId in their object key
        String stagingPrefix = STAGING_PREFIX + incidentId + "/";
        String archivePrefix = ARCHIVE_PREFIX + incidentId + "/evidences/";

        return findStagingObjects(tenantBucket, stagingPrefix)
                .flatMap(stagingKey -> archiveSingleEvidence(tenantBucket, stagingKey, archivePrefix)
                        .onErrorResume(ex -> {
                            log.error("Failed to archive evidence object {}: {}", stagingKey, ex.getMessage());
                            return Mono.empty();
                        }))
                .then()
                .doOnSuccess(v -> log.info("Evidence archival completed for incidentId={}", incidentId))
                .doOnError(ex -> log.error("Evidence archival failed for incidentId={}: {}",
                        incidentId, ex.getMessage()));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    /**
     * Lists all staging objects under the given prefix in the tenant's evidence bucket.
     *
     * <p>In a multi-tenant setup, each tenant has a dedicated bucket {@code tnt-{tenantId}}
     * (see {@link MediaFile#bucketNameFor(String)}) — evidence is uploaded there before
     * being archived into {@value INCIDENT_EVIDENCES_BUCKET}.</p>
     *
     * @param tenantBucket  the tenant's staging bucket, e.g. {@code tnt-{tenantId}}
     * @param stagingPrefix the object key prefix to list
     * @return a Flux of object keys found at the prefix
     */
    private Flux<String> findStagingObjects(String tenantBucket, String stagingPrefix) {
        return Flux.defer(() -> {
            try {
                Iterable<Result<Item>> results = minioClient.listObjects(
                        ListObjectsArgs.builder()
                                .bucket(tenantBucket)
                                .prefix(stagingPrefix)
                                .recursive(true)
                                .build());

                List<String> objectKeys = new ArrayList<>();
                for (Result<Item> result : results) {
                    objectKeys.add(result.get().objectName());
                }
                return Flux.fromIterable(objectKeys);
            } catch (Exception ex) {
                return Flux.error(new StorageException(
                        "Failed to list staging evidence objects at prefix=" + stagingPrefix, ex));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Copies a single evidence object from staging to the permanent archive location.
     *
     * @param tenantBucket  the tenant's staging bucket the object currently lives in
     * @param stagingKey    the source object key in the staging location
     * @param archivePrefix the destination prefix in the archive bucket
     * @return Mono completing when the copy and staging deletion are done
     */
    private Mono<Void> archiveSingleEvidence(String tenantBucket, String stagingKey, String archivePrefix) {
        String fileName = stagingKey.substring(stagingKey.lastIndexOf('/') + 1);
        String archiveKey = archivePrefix + fileName;

        return Mono.fromCallable(() -> {
                    // Copy from the tenant's staging bucket to the archive bucket (WORM-protected)
                    minioClient.copyObject(CopyObjectArgs.builder()
                            .bucket(INCIDENT_EVIDENCES_BUCKET)
                            .object(archiveKey)
                            .source(CopySource.builder()
                                    .bucket(tenantBucket)
                                    .object(stagingKey)
                                    .build())
                            .build());
                    log.debug("Evidence archived: {}/{} → {}/{}", tenantBucket, stagingKey, INCIDENT_EVIDENCES_BUCKET, archiveKey);

                    // Remove from staging location after successful copy
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(tenantBucket)
                            .object(stagingKey)
                            .build());
                    log.debug("Staging evidence removed: {}/{}", tenantBucket, stagingKey);

                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(ex -> new StorageException(
                        "Failed to archive evidence object: " + stagingKey + " → " + archiveKey, ex))
                .then();
    }
}
