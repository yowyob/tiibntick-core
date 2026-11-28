package com.yowyob.tiibntick.common.kafka;

import com.yowyob.tiibntick.common.vo.Address;
import com.yowyob.tiibntick.common.vo.GeoCoordinates;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TntEventMaskingTest {

    private final TntEventMasking masking = TntEventMasking.withDefaults();

    @Test
    void defaultPrecisionIsTwoDecimalsPerDecisionQo17() {
        assertThat(TntEventMasking.DEFAULT_COORDINATE_DECIMALS).isEqualTo(2);
        assertThat(masking.coordinateDecimals()).isEqualTo(2);
        assertThat(masking.approx(3.8666781)).isEqualTo(3.87);
        assertThat(masking.approx(11.5166742)).isEqualTo(11.52);
        assertThat(masking.approx(-4.0449999)).isEqualTo(-4.04);
        assertThat(masking.approx(4.045)).isEqualTo(4.05);
        assertThat(masking.approx(null)).isNull();
        assertThat(masking.approx(Double.NaN)).isNull();
    }

    @ParameterizedTest
    @CsvSource({"1, 3.9", "2, 3.87", "3, 3.867"})
    void precisionFollowsTheProperty(int decimals, double expected) {
        assertThat(new TntEventMasking(decimals).approx(3.8666781)).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 4, 7})
    void precisionOutsideTheQo17OptionsIsRefused(int decimals) {
        assertThatThrownBy(() -> new TntEventMasking(decimals))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(TntEventMasking.COORDINATE_DECIMALS_PROPERTY);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "Awa Ndiaye|Awa N.",
        "  Jean Paul   Mbarga |Jean M.",
        "Ébénézer élongo|Ébénézer É.",
        "Madonna|Madonna"
    })
    void displayNameIsFirstNameAndInitial(String fullName, String expected) {
        assertThat(TntEventMasking.displayName(fullName)).isEqualTo(expected);
    }

    @Test
    void displayNameFromParts() {
        assertThat(TntEventMasking.displayName("Awa", "ndiaye")).isEqualTo("Awa N.");
        assertThat(TntEventMasking.displayName(null, "Ndiaye")).isEqualTo("N.");
        assertThat(TntEventMasking.displayName("Awa Marie", null)).isEqualTo("Awa");
        assertThat(TntEventMasking.displayName(" ", null)).isNull();
        assertThat(TntEventMasking.displayName((String) null)).isNull();
    }

    @Test
    void addressKeepsOnlyDistrictCityCountryAndRoundedPosition() {
        Address exact = Address.builder()
            .street(TntPiiSentinels.of("street")).landmark(TntPiiSentinels.of("landmark"))
            .quarter("Bastos").city("Yaoundé").country("CM").postalCode(TntPiiSentinels.of("postalCode"))
            .coordinates(GeoCoordinates.of(TntPiiSentinels.EXACT_LATITUDE, TntPiiSentinels.EXACT_LONGITUDE))
            .build();
        TntEventMasking.MaskedAddress masked = masking.address(exact);
        assertThat(masked).isEqualTo(new TntEventMasking.MaskedAddress("Bastos", "Yaoundé", "CM", 3.87, 11.52));
        assertThat(masking.address(null, " ", "CM", (GeoCoordinates) null))
            .isEqualTo(new TntEventMasking.MaskedAddress(null, null, "CM", null, null));
        assertThat(masking.address((Address) null)).isNull();
    }

    @Test
    void maskedAddressMatchesTheSharedSchemaDefinition() {
        assertThat(EventSchemaValidator.errors(EventSchemaValidator.maskedAddress(),
            jsonOf(masking.address("Bastos", "Yaoundé", "CM", 3.8666781, 11.5166742)))).isEmpty();
        assertThat(EventSchemaValidator.errors(EventSchemaValidator.maskedAddress(),
            jsonOf(masking.address(null, null, null, (Double) null, null)))).isEmpty();
        assertThat(EventSchemaValidator.errors(EventSchemaValidator.maskedAddress(),
            "{\"district\":null,\"city\":\"Yaoundé\",\"country\":\"CM\",\"approxLatitude\":\"3.87\"}")).isNotEmpty();
    }

    @Test
    void amountsAreDecimalStrings() {
        assertThat(TntEventMasking.amount(new BigDecimal("1500.50"))).isEqualTo("1500.50");
        assertThat(TntEventMasking.amount(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(TntEventMasking.amount(null)).isNull();
    }

    /**
     * Spec §3.4: a snapshot built from sources filled with sentinels must not leak any of them. Done here
     * for the shared masking rules of every aggregate; each lot repeats it with its real snapshot (L2 parcel,
     * delivery, announcement; L3 incident; L4 dispute).
     */
    @ParameterizedTest
    @EnumSource(TntAggregateType.class)
    void noSentinelLeaksThroughTheMaskedSnapshot(TntAggregateType type) {
        String json = TntEventJson.write(event(type));
        assertThat(TntPiiSentinels.leaks(json)).isEmpty();
        assertThat(json).contains("Awa N.").contains("3.87").contains("Bastos");
        assertThat(EventSchemaValidator.errors(EventSchemaValidator.envelope(), json)).isEmpty();
    }

    @Test
    void sentinelDetectorFindsLeaks() {
        assertThat(TntPiiSentinels.leaks("{\"phone\":\"PII-sender.phone\",\"lat\":3.8666781}"))
            .containsExactly("PII-sender.phone", Double.toString(TntPiiSentinels.EXACT_LATITUDE));
        assertThat(TntPiiSentinels.leaks("{\"lat\":3.87}")).isEmpty();
        assertThat(TntPiiSentinels.leaks(null)).isEmpty();
    }

    // ── a raw source full of personal data, and the snapshot the masking rules allow to publish ──

    private record RawPerson(String name, String phone, String email) { }

    private record RawAddress(String street, String landmark, String district, String city, String country,
                              double latitude, double longitude) { }

    private TntAggregateEvent<Map<String, Object>> event(TntAggregateType type) {
        RawPerson sender = new RawPerson("Awa " + "Ndiaye", TntPiiSentinels.of("sender.phone"), TntPiiSentinels.of("sender.email"));
        RawPerson recipient = new RawPerson("Paul Biya" + "nkeng", TntPiiSentinels.of("recipient.phone"),
            TntPiiSentinels.of("recipient.email"));
        RawAddress address = new RawAddress(TntPiiSentinels.of("street"), TntPiiSentinels.of("landmark"), "Bastos",
            "Yaoundé", "CM", TntPiiSentinels.EXACT_LATITUDE, TntPiiSentinels.EXACT_LONGITUDE);

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", UUID.randomUUID().toString());
        snapshot.put("status", "CREATED");
        snapshot.put("senderDisplayName", TntEventMasking.displayName(sender.name()));
        snapshot.put("recipientDisplayName", TntEventMasking.displayName(recipient.name()));
        snapshot.put("deliveryAddress", masking.address(address.district(), address.city(), address.country(),
            address.latitude(), address.longitude()));
        snapshot.put("approxLatitude", masking.approx(address.latitude()));
        snapshot.put("approxLongitude", masking.approx(address.longitude()));
        snapshot.put("agreedAmount", TntEventMasking.amount(new BigDecimal("2500")));
        return TntAggregateEvent.<Map<String, Object>>builder(type)
            .eventTypeFor("CREATED").aggregateId(UUID.randomUUID()).trigger("CREATE")
            .actor(TntEventActor.of(TntEventActor.Type.SENDER, UUID.randomUUID()))
            .payload(snapshot).build();
    }

    private static String jsonOf(Object value) {
        try {
            return TntEventJson.mapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
