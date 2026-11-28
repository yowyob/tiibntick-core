package com.yowyob.tiibntick.bootstrap.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Lot L1 guard: the "deliveries carrying this parcel, whatever their tenant" lookups cross tenant boundaries.
 * They exist for ONE path only, the retrieval of a parcel deposited at a relay point (a deposit is keyed on
 * the parcel id and the retrieval has no tenant context). No other class may call them:
 * <ul>
 *   <li>{@code DeliveryQueryUseCase.findDeliveriesByParcelIdAnyTenant} ← only {@code DeliveryLifecyclePortAdapter};</li>
 *   <li>{@code IDeliveryLifecyclePort.findStatesByParcelAnyTenant} ← only {@code GofpDeliveryRepositoryAdapter};</li>
 *   <li>gofp {@code DeliveryRepository.findByParcelId} (port) ← only {@code RelayDepositService};</li>
 *   <li>the persistence methods behind them ← only their own adapter / service.</li>
 * </ul>
 */
class ParcelLookupCallersArchitectureTest {

    private static final String CORE_QUERY_USE_CASE =
            "com.yowyob.tiibntick.core.delivery.application.port.in.DeliveryQueryUseCase";
    private static final String CORE_DELIVERY_REPOSITORY =
            "com.yowyob.tiibntick.core.delivery.application.port.out.DeliveryRepository";
    private static final String CORE_R2DBC_REPOSITORY =
            "com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcDeliveryRepository";
    private static final String GOFP_LIFECYCLE_PORT =
            "com.yowyob.tiibntick.core.gofreelancer.application.port.out.IDeliveryLifecyclePort";
    private static final String GOFP_DELIVERY_REPOSITORY_PORT =
            "com.yowyob.tiibntick.core.gofreelancer.application.port.out.DeliveryRepository";
    private static final String GOFP_R2DBC_REPOSITORY =
            "com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.repository.DeliveryRepository";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.yowyob.tiibntick");
    }

    private static DescribedPredicate<JavaMethodCall> callTo(String ownerType, String method) {
        return DescribedPredicate.describe("a call to " + ownerType + "." + method,
                call -> call.getTarget().getName().equals(method)
                        && call.getTarget().getOwner().isAssignableTo(ownerType));
    }

    private static void onlyCalledBy(String ownerType, String method, String allowedCaller) {
        noClasses().that().doNotHaveFullyQualifiedName(allowedCaller)
                .should().callMethodWhere(callTo(ownerType, method))
                .because(method + " crosses tenants and is reserved for relay deposit retrieval (lot L1)")
                .check(classes);
    }

    @Test
    @DisplayName("core use case: deliveries by parcel, any tenant ← only the gofp lifecycle port adapter")
    void coreUseCase() {
        onlyCalledBy(CORE_QUERY_USE_CASE, "findDeliveriesByParcelIdAnyTenant",
                "com.yowyob.tiibntick.core.gofreelancer.adapter.out.delivery.DeliveryLifecyclePortAdapter");
    }

    @Test
    @DisplayName("core persistence behind it ← only the core query service / repository adapter")
    void corePersistence() {
        onlyCalledBy(CORE_DELIVERY_REPOSITORY, "findByParcelIdNoTenant",
                "com.yowyob.tiibntick.core.delivery.application.service.DeliveryQueryService");
        onlyCalledBy(CORE_R2DBC_REPOSITORY, "findByParcelIdNoTenant",
                "com.yowyob.tiibntick.core.delivery.adapter.out.persistence.DeliveryRepositoryAdapter");
    }

    @Test
    @DisplayName("gofp lifecycle port: states by parcel, any tenant ← only the gofp delivery repository adapter")
    void gofpLifecyclePort() {
        onlyCalledBy(GOFP_LIFECYCLE_PORT, "findStatesByParcelAnyTenant",
                "com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.GofpDeliveryRepositoryAdapter");
    }

    @Test
    @DisplayName("gofp delivery by parcel ← only the relay deposit retrieval (RelayDepositService)")
    void gofpRepository() {
        onlyCalledBy(GOFP_DELIVERY_REPOSITORY_PORT, "findByParcelId",
                "com.yowyob.tiibntick.core.gofreelancer.application.service.RelayDepositService");
        onlyCalledBy(GOFP_R2DBC_REPOSITORY, "findByParcelId",
                "com.yowyob.tiibntick.core.gofreelancer.adapter.out.persistence.GofpDeliveryRepositoryAdapter");
    }
}
