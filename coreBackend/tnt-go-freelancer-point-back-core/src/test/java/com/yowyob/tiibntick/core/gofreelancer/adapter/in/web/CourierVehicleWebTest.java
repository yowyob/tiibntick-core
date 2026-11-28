package com.yowyob.tiibntick.core.gofreelancer.adapter.in.web;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FileStoragePort;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.service.CourierVehicleService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.GofpFileUploadService;
import com.yowyob.tiibntick.core.gofreelancer.application.service.RelayPointAccessGuard;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpRelayPointRepository;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.resource.application.port.out.FreelancerEquipmentRepository;
import com.yowyob.tiibntick.core.resource.application.port.out.ResourceEventPublisherPort;
import com.yowyob.tiibntick.core.resource.application.service.FreelancerFleetApplicationService;
import reactor.core.publisher.Flux;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lot C-54 — A1: the courier records his own vehicle (type, plate, 1 to 4 JPEG/PNG photos); nobody
 * else can change it, and the older path-id photo routes are no longer open.
 */
@DisplayName("Courier vehicle — recorded by the courier himself")
class CourierVehicleWebTest {

    static final UUID OWNER_USER = UUID.fromString("11111111-0000-0000-0000-000000000054");
    static final UUID OWNER_GOFP = UUID.fromString("22222222-0000-0000-0000-000000000054");
    static final UUID OWNER_CORE = UUID.fromString("66666666-0000-0000-0000-000000000054");
    static final UUID STRANGER_USER = UUID.fromString("33333333-0000-0000-0000-000000000054");
    static final UUID STRANGER_GOFP = UUID.fromString("44444444-0000-0000-0000-000000000054");

    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 9};

    private final GofpFreelancerRepository freelancers = mock(GofpFreelancerRepository.class);
    private final FreelancerVehicleRepository vehicleRepo = mock(FreelancerVehicleRepository.class);
    private final FileStoragePort storage = mock(FileStoragePort.class);
    private final GofpFileUploadService uploads = mock(GofpFileUploadService.class);
    private final Map<UUID, FreelancerVehicle> rows = new ConcurrentHashMap<>();
    /** tnt-resource-core: the real fleet service over an in-memory vehicle table. */
    private final Map<UUID, com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle> coreRows =
            new ConcurrentHashMap<>();
    private final com.yowyob.tiibntick.core.resource.application.port.out.FreelancerVehicleRepository coreRepo =
            mock(com.yowyob.tiibntick.core.resource.application.port.out.FreelancerVehicleRepository.class);
    private final FreelancerFleetApplicationService fleet = new FreelancerFleetApplicationService(coreRepo,
            mock(FreelancerEquipmentRepository.class), mock(ResourceEventPublisherPort.class, i -> Mono.empty()));

    @BeforeEach
    void setUp() {
        when(freelancers.findByCoreUserId(any())).thenReturn(Mono.empty());
        when(freelancers.findByCoreUserId(OWNER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(OWNER_GOFP).coreFreelancerId(OWNER_CORE).coreUserId(OWNER_USER).build()));
        when(freelancers.findByCoreUserId(STRANGER_USER)).thenReturn(Mono.just(GofpFreelancer.builder()
                .id(STRANGER_GOFP).coreUserId(STRANGER_USER).build()));
        when(vehicleRepo.findByFreelancerId(any())).thenAnswer(i ->
                Mono.justOrEmpty(rows.get(i.<UUID>getArgument(0))));
        when(vehicleRepo.save(any(FreelancerVehicle.class))).thenAnswer(i -> {
            FreelancerVehicle v = i.getArgument(0);
            rows.put(v.getFreelancerId(), v);
            return Mono.just(v);
        });
        when(storage.saveBytes(any(), anyString(), anyString())).thenAnswer(i ->
                Mono.just("uploads/images/vehicule_" + UUID.randomUUID() + "." + i.getArgument(2)));
        when(uploads.uploadVehicleFrontPhoto(any(), any())).thenReturn(Mono.just(new FreelancerVehicle()));

        when(coreRepo.save(any())).thenAnswer(i -> {
            com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle v = i.getArgument(0);
            coreRows.put(v.vehicleId(), v);
            return Mono.just(v);
        });
        when(coreRepo.findById(any())).thenAnswer(i -> Mono.justOrEmpty(coreRows.get(i.<UUID>getArgument(0))));
        when(coreRepo.findByOwnerActorId(any())).thenAnswer(i -> Flux.fromIterable(coreRows.values())
                .filter(v -> i.getArgument(0).equals(v.ownerActorId())));
        when(coreRepo.countByOwnerActorId(any())).thenAnswer(i -> Flux.fromIterable(coreRows.values())
                .filter(v -> i.getArgument(0).equals(v.ownerActorId())).count());
        when(coreRepo.existsByPlateNumberOnAnotherVehicle(any(), any())).thenAnswer(i -> Mono.just(
                coreRows.values().stream().anyMatch(v -> v.plateNumber().equals(i.getArgument(0))
                        && !v.vehicleId().equals(i.getArgument(1)))));
    }

    private WebTestClient as(UUID userId, String... roles) {
        TntSecurityContext ctx = TntSecurityContext.builder().userId(userId)
                .tenantId(UUID.randomUUID()).authenticated(userId != null).roles(Set.of(roles)).build();
        CourierVehicleService service = new CourierVehicleService(freelancers, vehicleRepo, storage, 64,
                fleet, fleet, fleet);
        RelayPointAccessGuard relayGuard = new RelayPointAccessGuard(mock(GofpRelayPointRepository.class), freelancers);
        return WebTestClient.bindToController(
                        new CourierVehicleController(service),
                        new GofpFileUploadController(uploads, relayGuard, service))
                .controllerAdvice(new GlobalExceptionHandler())
                .argumentResolvers(cfg -> cfg.addCustomResolver(
                        new RelayPointAccessControllerTest.FixedContextResolver(ctx)))
                .build();
    }

    private static MultipartBodyBuilder file(byte[] bytes, String name) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return name; }
        }).contentType(MediaType.APPLICATION_OCTET_STREAM);
        return body;
    }

    private WebTestClient.ResponseSpec addPhoto(WebTestClient c, byte[] bytes, String name, boolean reset) {
        return c.post().uri("/api/v1/gofp/freelancer-profiles/me/vehicle/photos?reset=" + reset)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file(bytes, name).build()))
                .exchange();
    }

    @Test
    @DisplayName("the courier records type (app word) and plate; stored canonical, under his gofp id")
    void recordsTypeAndPlate() {
        as(OWNER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "taxi", "plateNumber", " lt 123 ab ", "maxCapacityKg", 350))
                .exchange().expectStatus().isOk()
                .expectBody()
                .jsonPath("$.vehicleType").isEqualTo("CAR")
                .jsonPath("$.category").isEqualTo("taxi")
                .jsonPath("$.plateNumber").isEqualTo("LT 123 AB")
                .jsonPath("$.maxCapacityKg").isEqualTo(350.0);
        FreelancerVehicle stored = rows.get(OWNER_GOFP);
        org.assertj.core.api.Assertions.assertThat(stored.getVehicleType()).isEqualTo("CAR");
        org.assertj.core.api.Assertions.assertThat(rows).doesNotContainKey(STRANGER_GOFP);
    }

    @Test
    @DisplayName("unknown type or bad plate → 400; no token → 403")
    void validation() {
        as(OWNER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "avion", "plateNumber", "LT123", "maxCapacityKg", 10)).exchange()
                .expectStatus().isBadRequest();
        as(OWNER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "moto", "plateNumber", "<script>", "maxCapacityKg", 10)).exchange()
                .expectStatus().isBadRequest();
        as(null).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "moto", "plateNumber", "LT123", "maxCapacityKg", 10)).exchange()
                .expectStatus().isForbidden();
    }

    @Test
    @DisplayName("lot L3: maximum capacity missing or not positive → 400, nothing written anywhere")
    void capacityIsMandatory() {
        for (Map<String, Object> body : java.util.List.<Map<String, Object>>of(
                Map.of("vehicleType", "moto", "plateNumber", "LT123"),
                Map.of("vehicleType", "moto", "plateNumber", "LT123", "maxCapacityKg", 0),
                Map.of("vehicleType", "moto", "plateNumber", "LT123", "maxCapacityKg", -5))) {
            as(OWNER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                    .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange()
                    .expectStatus().isBadRequest();
        }
        org.assertj.core.api.Assertions.assertThat(rows).isEmpty();
        org.assertj.core.api.Assertions.assertThat(coreRows).isEmpty();
    }

    @Test
    @DisplayName("lot L3: the vehicle is the courier's personal vehicle in tnt-resource-core; a new PUT updates it")
    void delegatedToResourceCore() {
        as(OWNER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "SCOOTER", "plateNumber", "LT 1", "maxCapacityKg", 20))
                .exchange().expectStatus().isOk();
        org.assertj.core.api.Assertions.assertThat(coreRows).hasSize(1);
        var core = coreRows.values().iterator().next();
        org.assertj.core.api.Assertions.assertThat(core.ownerActorId()).isEqualTo(OWNER_USER);
        org.assertj.core.api.Assertions.assertThat(core.ownerOrgId()).isNull();
        org.assertj.core.api.Assertions.assertThat(core.type())
                .isEqualTo(com.yowyob.tiibntick.core.resource.domain.model.VehicleType.SCOOTER);
        org.assertj.core.api.Assertions.assertThat(rows.get(OWNER_GOFP).getCoreVehicleId()).isEqualTo(core.vehicleId());
        org.assertj.core.api.Assertions.assertThat(rows.get(OWNER_GOFP).getVehicleType()).isEqualTo("MOTORBIKE");

        as(OWNER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "tricycle", "plateNumber", "LT 2", "maxCapacityKg", 300))
                .exchange().expectStatus().isOk();
        org.assertj.core.api.Assertions.assertThat(coreRows).hasSize(1);
        var updated = coreRows.get(core.vehicleId());
        org.assertj.core.api.Assertions.assertThat(updated.plateNumber()).isEqualTo("LT 2");
        org.assertj.core.api.Assertions.assertThat(updated.maxCapacityKg()).isEqualTo(300.0);
        org.assertj.core.api.Assertions.assertThat(updated.type())
                .isEqualTo(com.yowyob.tiibntick.core.resource.domain.model.VehicleType.TRICYCLE);

        as(OWNER_USER).get().uri("/api/v1/gofp/freelancer-profiles/me/vehicle").exchange()
                .expectStatus().isOk().expectBody()
                .jsonPath("$.category").isEqualTo("tricycle")
                .jsonPath("$.maxCapacityKg").isEqualTo(300.0);
    }

    @Test
    @DisplayName("lot L3: a plate registered on another courier's vehicle → 409, the local row is not written")
    void plateOfAnotherVehicle() {
        as(STRANGER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "moto", "plateNumber", "LT 9", "maxCapacityKg", 40))
                .exchange().expectStatus().isOk();
        as(OWNER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "moto", "plateNumber", "lt 9", "maxCapacityKg", 40))
                .exchange().expectStatus().isEqualTo(409);
        org.assertj.core.api.Assertions.assertThat(rows).doesNotContainKey(OWNER_GOFP);
        org.assertj.core.api.Assertions.assertThat(coreRows).hasSize(1);
    }

    @Test
    @DisplayName("lot L3: a core vehicle written without its local row (failed call) is found by plate, not duplicated")
    void resumesAfterAPartialWrite() {
        var orphan = com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle.registerPersonal(null,
                OWNER_USER, com.yowyob.tiibntick.core.resource.domain.model.VehicleType.MOTORCYCLE, null, null,
                "LT 5", 30.0, null, null, null, null, null);
        coreRows.put(orphan.vehicleId(), orphan);
        as(OWNER_USER).put().uri("/api/v1/gofp/freelancer-profiles/me/vehicle")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("vehicleType", "moto", "plateNumber", "LT 5", "maxCapacityKg", 45))
                .exchange().expectStatus().isOk();
        org.assertj.core.api.Assertions.assertThat(coreRows).hasSize(1);
        org.assertj.core.api.Assertions.assertThat(rows.get(OWNER_GOFP).getCoreVehicleId()).isEqualTo(orphan.vehicleId());
        org.assertj.core.api.Assertions.assertThat(coreRows.get(orphan.vehicleId()).maxCapacityKg()).isEqualTo(45.0);
    }

    @Test
    @DisplayName("photos: JPEG and PNG accepted, four at most, reset replaces; other bytes 415, too big 413")
    void photos() {
        WebTestClient c = as(OWNER_USER);
        addPhoto(c, JPEG, "a.jpg", true).expectStatus().isOk().expectBody().jsonPath("$.photoUrls.length()").isEqualTo(1);
        addPhoto(c, PNG, "b.png", false).expectStatus().isOk();
        addPhoto(c, JPEG, "c.jpg", false).expectStatus().isOk();
        addPhoto(c, JPEG, "d.jpg", false).expectStatus().isOk().expectBody().jsonPath("$.photoUrls.length()").isEqualTo(4);
        addPhoto(c, JPEG, "e.jpg", false).expectStatus().isBadRequest();
        addPhoto(c, "not an image".getBytes(), "x.jpg", true).expectStatus().isEqualTo(415);
        addPhoto(c, new byte[200], "big.jpg", true).expectStatus().isEqualTo(413);
        addPhoto(c, JPEG, "again.jpg", true).expectStatus().isOk().expectBody().jsonPath("$.photoUrls.length()").isEqualTo(1);
    }

    @Test
    @DisplayName("older photo route: another courier's vehicle → 403; own vehicle (by core id) stored under the gofp id")
    void legacyRouteGuarded() {
        as(STRANGER_USER).post().uri("/api/v1/gofp/files/freelancers/{id}/vehicle/front-photo", OWNER_GOFP)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file(JPEG, "a.jpg").build()))
                .exchange().expectStatus().isForbidden();
        verify(uploads, never()).uploadVehicleFrontPhoto(any(), any());

        as(OWNER_USER).post().uri("/api/v1/gofp/files/freelancers/{id}/vehicle/front-photo", OWNER_CORE)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(file(JPEG, "a.jpg").build()))
                .exchange().expectStatus().isOk();
        verify(uploads).uploadVehicleFrontPhoto(eq(OWNER_GOFP), any());
    }
}
