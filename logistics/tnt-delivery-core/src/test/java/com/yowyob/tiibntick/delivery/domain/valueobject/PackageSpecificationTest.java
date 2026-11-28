package com.yowyob.tiibntick.delivery.domain.valueobject;

import com.yowyob.tiibntick.core.delivery.domain.exception.DeliveryDomainException;
import com.yowyob.tiibntick.core.delivery.domain.model.valueobject.PackageSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PackageSpecificationTest {

    @Test
    @DisplayName("the 7-argument constructor leaves designation and thickness empty")
    void compatConstructorHasNoDesignationNorThickness() {
        PackageSpecification spec = new PackageSpecification(2.0, 20, 10, 30, true, false, "fragile glass");

        assertThat(spec.designation()).isNull();
        assertThat(spec.thicknessCm()).isNull();
        assertThat(spec.description()).isEqualTo("fragile glass");
    }

    @Test
    @DisplayName("designation and thickness are carried by the specification")
    void carriesDesignationAndThickness() {
        PackageSpecification spec = new PackageSpecification(
                12.5, 60, 40, 80, false, false, "washing machine", "Carton électroménager", 5.5);

        assertThat(spec.designation()).isEqualTo("Carton électroménager");
        assertThat(spec.thicknessCm()).isEqualTo(5.5);
    }

    @Test
    @DisplayName("a given thickness must be positive")
    void rejectsNonPositiveThickness() {
        assertThatThrownBy(() -> new PackageSpecification(1, 10, 10, 10, false, false, null, null, 0.0))
                .isInstanceOf(DeliveryDomainException.class)
                .hasMessageContaining("thickness");
    }

    @Test
    @DisplayName("weight and dimensions rules are unchanged")
    void keepsExistingRules() {
        assertThatThrownBy(() -> new PackageSpecification(0, 10, 10, 10, false, false, null))
                .isInstanceOf(DeliveryDomainException.class);
        assertThatThrownBy(() -> new PackageSpecification(1, 10, -1, 10, false, false, null))
                .isInstanceOf(DeliveryDomainException.class);
    }
}
