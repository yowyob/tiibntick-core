package com.yowyob.tiibntick.core.incident.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentBlockchainHashService;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentRiskScoringService;
import com.yowyob.tiibntick.core.incident.domain.service.IncidentTriageService;
import com.yowyob.tiibntick.core.incident.domain.model.DriverCandidate;
import com.yowyob.tiibntick.core.incident.domain.model.VehicleInfo;
import com.yowyob.tiibntick.core.incident.port.outbound.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.EnableR2dbcAuditing;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

/**
 * Spring configuration wiring domain services, no-op port beans and R2DBC auditing for tnt-incident-core.
 *
 * <p>Part of the tnt-incident-core module - TiiBnTick Logistics Layer.
 *
 * @author MANFOUO Braun
 * @version 0.0.1
 * @since TiiBnTick Core 0.0.1
 */

@Configuration
@EnableScheduling
//@EnableR2dbcAuditing
@ComponentScan(basePackages = "com.yowyob.tiibntick.core.incident")
@EnableR2dbcRepositories(basePackages = "com.yowyob.tiibntick.core.incident.adapter.persistence.repository")
public class IncidentCoreConfig {

    @Bean
    public IncidentRiskScoringService incidentRiskScoringService() {
        return new IncidentRiskScoringService();
    }

    @Bean
    public IncidentTriageService incidentTriageService() {
        return new IncidentTriageService();
    }

    @Bean
    public IncidentBlockchainHashService incidentBlockchainHashService() {
        return new IncidentBlockchainHashService();
    }

    @Bean
    @ConditionalOnMissingBean(name = "incidentObjectMapper")
    public ObjectMapper incidentObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    @ConditionalOnMissingBean(INotificationPort.class)
    public INotificationPort noOpNotificationPort() {
        return new INotificationPort() {
            @Override public Mono<Void> notifyActor(UUID a, String t, String b, String tp, UUID i) { return Mono.empty(); }
            @Override public Mono<Void> notifyActors(List<UUID> a, String t, String b, String tp, UUID i) { return Mono.empty(); }
            @Override public Mono<Void> notifyAgency(UUID ag, String t, String b, String tp, UUID i) { return Mono.empty(); }
        };
    }

    @Bean
    @ConditionalOnMissingBean(IPaymentFreezePort.class)
    public IPaymentFreezePort noOpPaymentFreezePort() {
        return new IPaymentFreezePort() {
            @Override public Mono<Void> freezePayment(UUID m, String r) { return Mono.empty(); }
            @Override public Mono<Void> unfreezePayment(UUID m, String r) { return Mono.empty(); }
        };
    }

    @Bean
    @ConditionalOnMissingBean(IActorReputationPort.class)
    public IActorReputationPort noOpActorReputationPort() {
        return new IActorReputationPort() {
            @Override public Mono<Void> decreaseReputation(UUID a, double p, String r) { return Mono.empty(); }
            @Override public Mono<Void> flagForFraud(UUID a, UUID i, String e) { return Mono.empty(); }
            @Override public Mono<Double> getReputationScore(UUID a) { return Mono.just(1.0); }
            @Override public Mono<Integer> getIncidentHistoryCount(UUID a) { return Mono.just(0); }
        };
    }

    @Bean
    @ConditionalOnMissingBean(IMediaStoragePort.class)
    public IMediaStoragePort noOpMediaStoragePort() {
        return (tenantId, incidentId) -> Mono.empty();
    }

    @Bean
    @ConditionalOnMissingBean(IBlockchainAuditPort.class)
    public IBlockchainAuditPort noOpBlockchainAuditPort() {
        return new IBlockchainAuditPort() {
            @Override public Mono<String> writeIncidentEvent(UUID id, String chain, String type, String payload) {
                return Mono.just("NO_OP_TX_" + UUID.randomUUID().toString().substring(0, 8));
            }
            @Override public Mono<Boolean> verifyChain(String chainId) { return Mono.just(true); }
            @Override public Mono<String> getParcelChainTailHash(UUID parcelId) {
                return Mono.just("GENESIS");
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(IDriverAvailabilityPort.class)
    public IDriverAvailabilityPort noOpDriverAvailabilityPort() {
        return new IDriverAvailabilityPort() {
            @Override
            public reactor.core.publisher.Flux<DriverCandidate> findEligibleReplacementDrivers(
                    UUID t, UUID a, double lat, double lng, double cap, String cat) {
                return reactor.core.publisher.Flux.empty();
            }
            @Override
            public reactor.core.publisher.Flux<DriverCandidate> findEligibleReplacementDrivers(
                    UUID tenantId, UUID agencyId, double lat, double lng, double requiredCapacityKg,
                    String vehicleCategory, String freelancerOrgId, boolean searchWithinFreelancerOrg) {
                return reactor.core.publisher.Flux.empty();
            }
            @Override public Mono<Boolean> isDriverAvailable(UUID driverId) { return Mono.just(false); }
        };
    }

    @Bean
    @ConditionalOnMissingBean(IVehicleCompatibilityPort.class)
    public IVehicleCompatibilityPort noOpVehicleCompatibilityPort() {
        return new IVehicleCompatibilityPort() {
            @Override public Mono<VehicleInfo> getVehicleInfo(UUID vehicleId) { return Mono.empty(); }
            @Override public Mono<Boolean> isVehicleAvailable(UUID vehicleId) { return Mono.just(false); }
            @Override public Mono<Void> placeVehicleInSubstitution(UUID vehicleId, UUID borrowingAgencyId) { return Mono.empty(); }
            @Override public Mono<Void> releaseVehicleFromSubstitution(UUID vehicleId) { return Mono.empty(); }
        };
    }

    @Bean
    @ConditionalOnMissingBean(IRouteOptimizerPort.class)
    public IRouteOptimizerPort noOpRouteOptimizerPort() {
        return new IRouteOptimizerPort() {
            @Override public Mono<NearestHubResult> findNearestHub(double lat, double lng, UUID tenantId) { return Mono.empty(); }
            @Override public Mono<RerouteResult> rerouteFromCurrentPosition(UUID missionId, double lat, double lng) { return Mono.empty(); }
        };
    }

    @Bean
    @ConditionalOnMissingBean(IMissionStatusPort.class)
    public IMissionStatusPort noOpMissionStatusPort() {
        return new IMissionStatusPort() {
            @Override public Mono<Void> pauseMission(UUID m, UUID i) { return Mono.empty(); }
            @Override public Mono<Void> resumeMission(UUID m, UUID d, UUID v) { return Mono.empty(); }
            @Override public Mono<MissionSnapshot> getMissionSnapshot(UUID m) { return Mono.empty(); }
        };
    }
}
