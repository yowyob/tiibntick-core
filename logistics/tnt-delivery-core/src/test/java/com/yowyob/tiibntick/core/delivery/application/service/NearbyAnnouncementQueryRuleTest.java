package com.yowyob.tiibntick.core.delivery.application.service;

import com.yowyob.tiibntick.core.delivery.application.port.in.query.NearbyAnnouncementQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** règles de la requête des annonces proches (hors SQL). */
@DisplayName("NearbyAnnouncementQuery")
class NearbyAnnouncementQueryRuleTest {

    @Test
    @DisplayName("ville normalisée : casse, accents et espaces de bord ignorés")
    void cityNormalization() {
        assertThat(NearbyAnnouncementQuery.normalizeCity("  Yaoundé ")).isEqualTo("yaounde");
        assertThat(NearbyAnnouncementQuery.normalizeCity("DOUALA")).isEqualTo("douala");
        assertThat(NearbyAnnouncementQuery.normalizeCity(null)).isEmpty();
    }

    @Test
    @DisplayName("ni position ni ville → « rien de connu » (toutes les annonces ouvertes)")
    void knowsNothing() {
        assertThat(new NearbyAnnouncementQuery(null, null, 15, null).knowsNothing()).isTrue();
        assertThat(new NearbyAnnouncementQuery(null, null, 15, "  ").knowsNothing()).isTrue();
        assertThat(new NearbyAnnouncementQuery(null, null, 15, "Douala").knowsNothing()).isFalse();
        assertThat(new NearbyAnnouncementQuery(4.0, 9.7, 15, null).knowsNothing()).isFalse();
    }

    @Test
    @DisplayName("entrées invalides refusées : rayon ≤ 0, coordonnée seule, hors bornes")
    void invalidInput() {
        assertThatThrownBy(() -> new NearbyAnnouncementQuery(4.0, 9.7, 0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NearbyAnnouncementQuery(4.0, null, 15, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NearbyAnnouncementQuery(91.0, 9.7, 15, null)).isInstanceOf(IllegalArgumentException.class);
    }
}
