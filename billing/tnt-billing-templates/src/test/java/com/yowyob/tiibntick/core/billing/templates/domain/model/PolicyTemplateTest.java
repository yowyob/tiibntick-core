package com.yowyob.tiibntick.core.billing.templates.domain.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link PolicyTemplate} aggregate root.
 *
 * @author MANFOUO Braun
 * @version 1.0
 * @since 2026-05-26
 */
class PolicyTemplateTest {

    private PolicyTemplate template;

    @BeforeEach
    void setUp() {
        template = PolicyTemplate.createNew(
                "TPL-TEST", "Test Template", "Description",
                TemplateCategory.BASE,
                List.of(PolicyOwnerType.AGENCY, PolicyOwnerType.FREELANCER_ORG),
                List.of(
                        TemplateParameter.builder()
                                .key("basePrice").labelFr("Prix de base").labelEn("Base price")
                                .defaultValue("500").minValue("100").maxValue("50000")
                                .unit("XAF").type(ParameterType.MONEY)
                                .helpText("Minimum base price").build(),
                        TemplateParameter.builder()
                                .key("perKmRate").labelFr("Tarif km").labelEn("Per km rate")
                                .defaultValue("50").minValue("0").maxValue("500")
                                .unit("XAF/km").type(ParameterType.MONEY)
                                .helpText("Rate per km").build()
                ),
                "IF weight >= 0 THEN SET_BASE(500 XAF)"
        );
    }

    @Nested
    @DisplayName("createNew")
    class CreateNew {

        @Test
        @DisplayName("Should create a template with generated UUID and current timestamps")
        void shouldCreateWithGeneratedId() {
            assertThat(template.getId()).isNotNull();
            assertThat(template.getCreatedAt()).isNotNull();
            assertThat(template.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should create as active by default")
        void shouldBeActiveByDefault() {
            assertThat(template.isActive()).isTrue();
        }

        @Test
        @DisplayName("Should store all provided fields")
        void shouldStoreAllFields() {
            assertThat(template.getTemplateCode()).isEqualTo("TPL-TEST");
            assertThat(template.getName()).isEqualTo("Test Template");
            assertThat(template.getCategory()).isEqualTo(TemplateCategory.BASE);
            assertThat(template.getApplicableTo()).containsExactly(PolicyOwnerType.AGENCY, PolicyOwnerType.FREELANCER_ORG);
            assertThat(template.getParameters()).hasSize(2);
        }
    }

    @Nested
    @DisplayName("isApplicableTo")
    class IsApplicableTo {

        @Test
        @DisplayName("Should return true for applicable owner types")
        void shouldReturnTrueForApplicableTypes() {
            assertThat(template.isApplicableTo(PolicyOwnerType.AGENCY)).isTrue();
            assertThat(template.isApplicableTo(PolicyOwnerType.FREELANCER_ORG)).isTrue();
        }

        @Test
        @DisplayName("Should return false for non-applicable owner types")
        void shouldReturnFalseForNonApplicableTypes() {
            assertThat(template.isApplicableTo(PolicyOwnerType.POINT)).isFalse();
            assertThat(template.isApplicableTo(PolicyOwnerType.LINK)).isFalse();
            assertThat(template.isApplicableTo(PolicyOwnerType.MARKET)).isFalse();
        }
    }

    @Nested
    @DisplayName("findParameter")
    class FindParameter {

        @Test
        @DisplayName("Should find parameter by key")
        void shouldFindByKey() {
            TemplateParameter found = template.findParameter("basePrice");
            assertThat(found).isNotNull();
            assertThat(found.getKey()).isEqualTo("basePrice");
            assertThat(found.getDefaultValue()).isEqualTo("500");
        }

        @Test
        @DisplayName("Should return null for unknown key")
        void shouldReturnNullForUnknownKey() {
            assertThat(template.findParameter("unknownParam")).isNull();
        }
    }

    @Nested
    @DisplayName("validateCustomValues")
    class ValidateCustomValues {

        @Test
        @DisplayName("Should pass validation with empty overrides")
        void shouldPassWithEmptyOverrides() {
            List<String> errors = template.validateCustomValues(Map.of());
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("Should pass validation with valid values within bounds")
        void shouldPassWithValidValues() {
            List<String> errors = template.validateCustomValues(Map.of("basePrice", "700", "perKmRate", "60"));
            assertThat(errors).isEmpty();
        }

        @Test
        @DisplayName("Should fail validation for value below minimum")
        void shouldFailForValueBelowMin() {
            List<String> errors = template.validateCustomValues(Map.of("basePrice", "50"));
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0)).contains("below minimum");
        }

        @Test
        @DisplayName("Should fail validation for value above maximum")
        void shouldFailForValueAboveMax() {
            List<String> errors = template.validateCustomValues(Map.of("basePrice", "100000"));
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0)).contains("exceeds maximum");
        }

        @Test
        @DisplayName("Should fail validation for unknown parameter key")
        void shouldFailForUnknownKey() {
            List<String> errors = template.validateCustomValues(Map.of("unknownParam", "999"));
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0)).contains("Unknown parameter key");
        }

        @Test
        @DisplayName("Should fail validation for non-numeric value on MONEY type")
        void shouldFailForNonNumericValue() {
            List<String> errors = template.validateCustomValues(Map.of("basePrice", "not_a_number"));
            assertThat(errors).isNotEmpty();
            assertThat(errors.get(0)).contains("not a valid number");
        }
    }

    @Nested
    @DisplayName("activate / deactivate")
    class ActivateDeactivate {

        @Test
        @DisplayName("Should return inactive copy when deactivated")
        void shouldDeactivate() {
            PolicyTemplate deactivated = template.deactivate();
            assertThat(deactivated.isActive()).isFalse();
            assertThat(template.isActive()).isTrue(); // Original unchanged (immutable)
        }

        @Test
        @DisplayName("Should return active copy when activated")
        void shouldActivate() {
            PolicyTemplate deactivated = template.deactivate();
            PolicyTemplate reactivated = deactivated.activate();
            assertThat(reactivated.isActive()).isTrue();
        }
    }
}
