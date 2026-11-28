package com.yowyob.tiibntick.core.gofreelancer.application.service;

import com.yowyob.tiibntick.core.auth.domain.model.TntSecurityContext;
import com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.FreelancerVehicleRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.GofpFreelancerRepository;
import com.yowyob.tiibntick.core.gofreelancer.application.port.out.FileStoragePort;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.FreelancerVehicle;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.GofpFreelancer;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleCategory;
import com.yowyob.tiibntick.core.gofreelancer.domain.model.enums.vehicle.VehicleType;
import com.yowyob.tiibntick.core.resource.application.port.in.AddPersonalVehicleCommand;
import com.yowyob.tiibntick.core.resource.application.port.in.AddPersonalVehicleUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.GetFreelancerVehicleUseCase;
import com.yowyob.tiibntick.core.resource.application.port.in.UpdateFreelancerVehicleCommand;
import com.yowyob.tiibntick.core.resource.application.port.in.UpdateFreelancerVehicleUseCase;
import com.yowyob.tiibntick.core.resource.domain.exception.FreelancerVehicleNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Lot C-54 — the courier registers his own vehicle: its type, its plate and one to four photos
 * (JPEG/PNG, bounded size). Everything is kept on the {@code gofreelancer_vehicles} row keyed by the
 * courier's {@code gofp_freelancers.id} — the id the matching reads it with.
 *
 * <p>Lot L3: the vehicle itself (type, plate, mandatory maximum capacity) is owned by tnt-resource-core as the
 * courier's personal vehicle (owner = his actor id), written synchronously on every {@code PUT /me/vehicle}; the
 * local row keeps its {@code core_vehicle_id}, the photos and a read copy of the type and plate for the matching.
 *
 * <p>Guard: the profile is always resolved from the token ({@code /me}); the older path-id routes go
 * through {@link #requireSelfOrStaff} — another courier gets a 403.
 */
@Slf4j
@Service
public class CourierVehicleService {

    public static final int MAX_PHOTOS = 4;
    private static final Pattern PLATE = Pattern.compile("^[A-Z0-9][A-Z0-9 -]{1,18}[A-Z0-9]$");

    private final GofpFreelancerRepository freelancers;
    private final FreelancerVehicleRepository vehicles;
    private final FileStoragePort storage;
    private final int maxPhotoBytes;
    private final AddPersonalVehicleUseCase addCoreVehicle;
    private final UpdateFreelancerVehicleUseCase updateCoreVehicle;
    private final GetFreelancerVehicleUseCase getCoreVehicle;

    public CourierVehicleService(GofpFreelancerRepository freelancers,
                                 FreelancerVehicleRepository vehicles,
                                 FileStoragePort storage,
                                 @Value("${tnt.gofp.vehicle.max-photo-bytes:5242880}") int maxPhotoBytes,
                                 AddPersonalVehicleUseCase addCoreVehicle,
                                 UpdateFreelancerVehicleUseCase updateCoreVehicle,
                                 GetFreelancerVehicleUseCase getCoreVehicle) {
        this.freelancers = freelancers;
        this.vehicles = vehicles;
        this.storage = storage;
        this.maxPhotoBytes = maxPhotoBytes;
        this.addCoreVehicle = addCoreVehicle;
        this.updateCoreVehicle = updateCoreVehicle;
        this.getCoreVehicle = getCoreVehicle;
    }

    /** What the courier sees of his own vehicle ({@code maxCapacityKg} read from tnt-resource-core). */
    public record VehicleView(String vehicleType, String category, String plateNumber, Double maxCapacityKg,
                              List<String> photoUrls) {
        static VehicleView of(FreelancerVehicle v, Double maxCapacityKg) {
            VehicleCategory c = VehicleCategory.parse(v.getVehicleType());
            return new VehicleView(v.getVehicleType(), c == null ? null : c.appWord(), v.getPlateNumber(),
                    maxCapacityKg, v.photoUrlList());
        }
    }

    // ── /me ─────────────────────────────────────────────────────────────

    public Mono<VehicleView> myVehicle(TntSecurityContext ctx) {
        return myProfile(ctx)
                .flatMap(this::vehicleOf)
                .flatMap(this::withCapacity)
                .defaultIfEmpty(new VehicleView(null, null, null, null, List.of()));
    }

    /**
     * Type (either vocabulary), plate and maximum capacity in kg (mandatory, {@code > 0}) of the caller's vehicle.
     * Written first to tnt-resource-core (personal vehicle of the caller's actor: created, or updated when it
     * already exists), then to the local row. A plate registered on another vehicle gives a 409.
     */
    public Mono<VehicleView> saveMyVehicle(TntSecurityContext ctx, String type, String plate, Double maxCapacityKg) {
        String canonical = VehicleCategory.canonicalName(type);
        if (canonical == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "vehicleType must be one of moto, tricycle, taxi, camion, bus"));
        }
        String normalizedPlate = plate == null ? "" : plate.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
        if (!PLATE.matcher(normalizedPlate).matches()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "plateNumber must be 3 to 20 letters, digits, spaces or dashes"));
        }
        if (maxCapacityKg == null || !(maxCapacityKg > 0) || maxCapacityKg.isInfinite()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "maxCapacityKg is required and must be greater than 0"));
        }
        var coreType = coreType(type, canonical);
        return myProfile(ctx)
                .flatMap(this::loadOrCreate)
                .flatMap(v -> saveCoreVehicle(ctx.userId(), v.getCoreVehicleId(), coreType, normalizedPlate,
                        maxCapacityKg)
                        .flatMap(core -> {
                            v.setCoreVehicleId(core.vehicleId());
                            v.setVehicleType(canonical);
                            v.setPlateNumber(normalizedPlate);
                            return vehicles.save(v).map(saved -> VehicleView.of(saved, core.maxCapacityKg()));
                        }));
    }

    /**
     * The caller's core vehicle: the one the local row points to when it is his, else his personal vehicle with
     * this plate (a previous call that wrote the core but not the local row), else a new one.
     */
    private Mono<com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle> saveCoreVehicle(
            UUID actorId, UUID coreVehicleId, com.yowyob.tiibntick.core.resource.domain.model.VehicleType type,
            String plate, double maxCapacityKg) {
        Mono<com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle> referenced = coreVehicleId == null
                ? Mono.empty()
                : getCoreVehicle.getVehicle(coreVehicleId)
                        .onErrorResume(FreelancerVehicleNotFoundException.class, e -> Mono.empty())
                        .filter(core -> actorId.equals(core.ownerActorId()));
        Mono<com.yowyob.tiibntick.core.resource.domain.model.FreelancerVehicle> samePlate = addCoreVehicle
                .getPersonalVehicles(actorId)
                .filter(core -> plate.equals(core.plateNumber()))
                .next();
        return referenced.switchIfEmpty(samePlate)
                .flatMap(core -> updateCoreVehicle.updateVehicle(new UpdateFreelancerVehicleCommand(core.vehicleId(),
                        type, core.brand(), core.model(), plate, maxCapacityKg, core.volumeM3(), core.fuelType(),
                        core.fuelConsumptionLPer100km())))
                .switchIfEmpty(Mono.defer(() -> addCoreVehicle.addPersonalVehicle(new AddPersonalVehicleCommand(
                        null, actorId, type, null, null, plate, maxCapacityKg, null, null, null, null, null))))
                .onErrorMap(IllegalStateException.class,
                        e -> new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage()))
                .onErrorMap(IllegalArgumentException.class,
                        e -> new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    /**
     * Core type of the declared vehicle: the precise Go type when the caller sent one (SCOOTER, VAN...), else the
     * category's canonical type.
     */
    static com.yowyob.tiibntick.core.resource.domain.model.VehicleType coreType(String raw, String canonical) {
        VehicleType go = VehicleType.valueOf(canonical);
        for (VehicleType t : VehicleType.values()) {
            if (raw != null && t.name().equalsIgnoreCase(raw.trim())) {
                go = t;
            }
        }
        return switch (go) {
            case MOTORBIKE -> com.yowyob.tiibntick.core.resource.domain.model.VehicleType.MOTORCYCLE;
            case SCOOTER -> com.yowyob.tiibntick.core.resource.domain.model.VehicleType.SCOOTER;
            case BIKE -> com.yowyob.tiibntick.core.resource.domain.model.VehicleType.BICYCLE;
            case TRICYCLE -> com.yowyob.tiibntick.core.resource.domain.model.VehicleType.TRICYCLE;
            case CAR -> com.yowyob.tiibntick.core.resource.domain.model.VehicleType.CAR;
            case TRUCK -> com.yowyob.tiibntick.core.resource.domain.model.VehicleType.TRUCK;
            case VAN -> com.yowyob.tiibntick.core.resource.domain.model.VehicleType.VAN;
            case BUS -> com.yowyob.tiibntick.core.resource.domain.model.VehicleType.BUS;
        };
    }

    /** The view with the maximum capacity read from tnt-resource-core ({@code null} while not delegated yet). */
    private Mono<VehicleView> withCapacity(FreelancerVehicle v) {
        if (v.getCoreVehicleId() == null) {
            return Mono.just(VehicleView.of(v, null));
        }
        return getCoreVehicle.getVehicle(v.getCoreVehicleId())
                .map(core -> VehicleView.of(v, core.maxCapacityKg()))
                .onErrorResume(FreelancerVehicleNotFoundException.class, e -> Mono.empty())
                .defaultIfEmpty(VehicleView.of(v, null));
    }

    /**
     * Adds one photo to the caller's vehicle ({@code reset} first clears the previous ones, so a
     * re-done KYC step replaces them). A fifth photo is refused (400).
     */
    public Mono<VehicleView> addMyPhoto(TntSecurityContext ctx, FilePart file, boolean reset) {
        if (file == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "file is required"));
        }
        return myProfile(ctx)
                .flatMap(this::loadOrCreate)
                .flatMap(v -> {
                    List<String> current = reset ? new ArrayList<>() : new ArrayList<>(storedPhotos(v));
                    if (current.size() >= MAX_PHOTOS) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "A vehicle has at most " + MAX_PHOTOS + " photos"));
                    }
                    return readImage(file)
                            .flatMap(img -> storage.saveBytes(img.bytes(), "vehicule", img.extension()))
                            .flatMap(path -> {
                                if (path == null || path.isBlank()) {
                                    return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                                            "The photo could not be stored"));
                                }
                                current.add(path);
                                v.setPhotoUrls(String.join("\n", current));
                                if (reset) {
                                    v.setFrontPhotoUrl(null);
                                    v.setBackPhotoUrl(null);
                                }
                                return vehicles.save(v);
                            });
                })
                .flatMap(this::withCapacity);
    }

    // ── guards & lookups shared with the other services ─────────────────

    /**
     * Older path-id routes: the path id must be the caller's own courier profile (gofp id or core
     * freelancer id), or the caller is staff. Resolves to the id the vehicle row is keyed by.
     */
    public Mono<UUID> requireSelfOrStaff(UUID pathFreelancerId, TntSecurityContext ctx) {
        UUID caller = ctx != null ? ctx.userId() : null;
        if (caller == null) {
            return Mono.error(new AccessDeniedException("An authenticated courier is required"));
        }
        if (RelayPointAccessGuard.isStaff(ctx)) {
            return Mono.just(pathFreelancerId);
        }
        return freelancers.findByCoreUserId(caller)
                .filter(me -> pathFreelancerId.equals(me.getId())
                        || pathFreelancerId.equals(me.getCoreFreelancerId()))
                .map(GofpFreelancer::getId)
                .switchIfEmpty(Mono.error(new AccessDeniedException(
                        "A courier may only change his own vehicle")));
    }

    /** The vehicle of a courier, under his gofp id first, then his core freelancer id (older rows). */
    public Mono<FreelancerVehicle> vehicleOf(GofpFreelancer f) {
        Mono<FreelancerVehicle> byGofp = vehicles.findByFreelancerId(f.getId());
        if (f.getCoreFreelancerId() == null) return byGofp;
        return byGofp.switchIfEmpty(Mono.defer(() -> vehicles.findByFreelancerId(f.getCoreFreelancerId())));
    }

    private Mono<GofpFreelancer> myProfile(TntSecurityContext ctx) {
        UUID caller = ctx != null ? ctx.userId() : null;
        if (caller == null) {
            return Mono.error(new AccessDeniedException("An authenticated courier is required"));
        }
        return freelancers.findByCoreUserId(caller)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Courier profile not found for the caller")));
    }

    private Mono<FreelancerVehicle> loadOrCreate(GofpFreelancer f) {
        return vehicleOf(f).switchIfEmpty(Mono.fromSupplier(() -> FreelancerVehicle.builder()
                .id(UUID.randomUUID())
                .freelancerId(f.getId())
                .build()));
    }

    private static List<String> storedPhotos(FreelancerVehicle v) {
        return v.photoUrlList();
    }

    // ── image validation ────────────────────────────────────────────────

    record Image(byte[] bytes, String extension) { }

    /** Reads at most {@code maxPhotoBytes}; accepts JPEG and PNG by their magic bytes only. */
    Mono<Image> readImage(FilePart file) {
        return DataBufferUtils.join(file.content(), maxPhotoBytes)
                .map(buf -> {
                    byte[] bytes = new byte[buf.readableByteCount()];
                    buf.read(bytes);
                    DataBufferUtils.release(buf);
                    return bytes;
                })
                .onErrorMap(DataBufferLimitException.class, e -> new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "A vehicle photo is at most " + maxPhotoBytes + " bytes"))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Empty file")))
                .flatMap(bytes -> {
                    String ext = imageExtension(bytes);
                    if (ext == null) {
                        return Mono.error(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                                "A vehicle photo must be a JPEG or PNG image"));
                    }
                    return Mono.just(new Image(bytes, ext));
                });
    }

    static String imageExtension(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return "png";
        }
        return null;
    }
}
