package com.yowyob.tiibntick.core.delivery.adapter.out.persistence;

import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.entity.DeliveryAnnouncementEntity;
import com.yowyob.tiibntick.core.delivery.adapter.out.persistence.repository.R2dbcAnnouncementRepository;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.r2dbc.repository.support.R2dbcRepositoryFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * la requête des annonces proches, sur un vrai Postgres avec le changelog
 * Liquibase réel du module ({@code tnt_delivery_announcements}).
 *
 * <p>Géographie de test : Akwa (Douala) est le point du livreur. Repères :
 * Bonabéri ≈ 5 km, Logbessou ≈ 10 km (visible), Yassa ≈ 20 km (invisible), Yaoundé ≈ 200 km.
 */
@Testcontainers
@Tag("integration")
class NearbyAnnouncementsQueryIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("tnt_delivery_nearby_test")
            .withUsername("tnt_test")
            .withPassword("tnt_test_secret");

    static final UUID TENANT = UUID.randomUUID();
    static final UUID OTHER_TENANT = UUID.randomUUID();

    /** Point du livreur : Akwa, Douala. */
    static final double LAT = 4.0511;
    static final double LNG = 9.7679;

    static final String NEAR_5KM = "near-5km";
    static final String AT_10KM = "at-10km";
    static final String AT_20KM = "at-20km";
    /** Inside the bounding box of a 15 km radius but outside the circle (≈ 18.9 km on the diagonal). */
    static final String CORNER_19KM = "corner-19km";
    static final String YAOUNDE = "yaounde";
    static final String DOUALA_NO_COORDS = "douala-no-coords";
    static final String YAOUNDE_NO_COORDS = "yaounde-no-coords";
    static final String ASSIGNED_NEAR = "assigned-near";
    static final String OTHER_TENANT_NEAR = "other-tenant-near";

    static R2dbcAnnouncementRepository repository;

    @BeforeAll
    static void migrateAndSeed() throws Exception {
        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(conn));
            new Liquibase("db/changelog/tnt-delivery-master.yaml", new ClassLoaderResourceAccessor(), database)
                    .update(new Contexts(), new LabelExpression());
            conn.setAutoCommit(true); // Liquibase laisse la connexion en autoCommit=false

            // ≈ distances à vol d'oiseau depuis (4.0511, 9.7679), 1° de latitude ≈ 111,2 km
            insert(conn, TENANT, NEAR_5KM, "PUBLISHED", "Douala", LAT + 0.045, LNG);
            insert(conn, TENANT, AT_10KM, "PUBLISHED", "Douala", LAT + 0.090, LNG);
            insert(conn, TENANT, AT_20KM, "IN_NEGOTIATION", "Douala", LAT - 0.180, LNG);
            insert(conn, TENANT, CORNER_19KM, "PUBLISHED", "Douala", LAT + 0.120, LNG + 0.1203);
            insert(conn, TENANT, YAOUNDE, "PUBLISHED", "Yaoundé", 3.8480, 11.5021);
            insert(conn, TENANT, DOUALA_NO_COORDS, "PUBLISHED", "DOUALA ", null, null);
            insert(conn, TENANT, YAOUNDE_NO_COORDS, "PUBLISHED", "Yaounde", null, null);
            insert(conn, TENANT, ASSIGNED_NEAR, "ASSIGNED", "Douala", LAT, LNG);
            insert(conn, OTHER_TENANT, OTHER_TENANT_NEAR, "PUBLISHED", "Douala", LAT, LNG);
        }
        PostgresqlConnectionConfiguration config = PostgresqlConnectionConfiguration.builder()
                .host(POSTGRES.getHost())
                .port(POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT))
                .database(POSTGRES.getDatabaseName())
                .username(POSTGRES.getUsername())
                .password(POSTGRES.getPassword())
                .build();
        repository = new R2dbcRepositoryFactory(new R2dbcEntityTemplate(new PostgresqlConnectionFactory(config)))
                .getRepository(R2dbcAnnouncementRepository.class);
    }

    private static void insert(Connection conn, UUID tenant, String title, String status, String city,
                               Double lat, Double lng) throws Exception {
        UUID parcel = UUID.randomUUID();
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tnt_parcels (id, weight_kg, width_cm, height_cm, length_cm, created_at, updated_at)
                VALUES (?, 1, 10, 10, 10, now(), now())""")) {
            ps.setObject(1, parcel);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO tnt_delivery_announcements
                  (id, tenant_id, client_id, title, currency, parcel_id, status, urgency,
                   pickup_street, pickup_city, pickup_latitude, pickup_longitude,
                   delivery_street, delivery_city, recipient_name, recipient_phone, created_at, updated_at,
                   pricing_mode)
                VALUES (?, ?, ?, ?, 'XAF', ?, ?, 'NORMAL', 'rue', ?, ?, ?, 'rue', 'Douala', 'R', '+237600000000',
                        now(), now(), 'QUOTE_REQUEST')""")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setObject(2, tenant);
            ps.setObject(3, UUID.randomUUID());
            ps.setString(4, title);
            ps.setObject(5, parcel);
            ps.setString(6, status);
            ps.setString(7, city);
            ps.setObject(8, lat);
            ps.setObject(9, lng);
            ps.executeUpdate();
        }
    }

    /** Boîte englobante du rayon, comme la calcule l'adaptateur. */
    private static List<String> near(double lat, double lng, double radiusKm, String city) {
        double dLat = radiusKm / 111.2;
        double dLng = radiusKm / (111.2 * Math.cos(Math.toRadians(lat)));
        return titles(repository.findOpenNear(TENANT, true, lat, lng,
                lat - dLat, lat + dLat, lng - dLng, lng + dLng, radiusKm, city));
    }

    private static List<String> cityOnly(String city) {
        return titles(repository.findOpenNear(TENANT, false, 0, 0, 0, 0, 0, 0, 0, city));
    }

    private static List<String> titles(reactor.core.publisher.Flux<DeliveryAnnouncementEntity> flux) {
        return flux.map(DeliveryAnnouncementEntity::getTitle).collectList().block();
    }

    @Test
    @DisplayName("rayon 15 km depuis Akwa : 5 km et 10 km visibles ; 20 km et Yaoundé invisibles")
    void radius15km() {
        List<String> seen = near(LAT, LNG, 15, "douala");
        assertThat(seen).contains(NEAR_5KM, AT_10KM).doesNotContain(AT_20KM, YAOUNDE);
        assertThat(seen).as("the distance filter, not only the bounding box, decides").doesNotContain(CORNER_19KM);
    }

    @Test
    @DisplayName("Douala ne voit pas Yaoundé (avec ou sans coordonnées)")
    void doualaDoesNotSeeYaounde() {
        assertThat(near(LAT, LNG, 15, "douala")).doesNotContain(YAOUNDE, YAOUNDE_NO_COORDS);
    }

    @Test
    @DisplayName("annonce sans coordonnées : repli même ville (insensible à la casse, aux accents, aux espaces)")
    void cityFallbackForAnnouncementsWithoutCoordinates() {
        assertThat(near(LAT, LNG, 15, "douala")).contains(DOUALA_NO_COORDS);
        assertThat(near(3.8480, 11.5021, 15, "yaounde")).contains(YAOUNDE, YAOUNDE_NO_COORDS)
                .doesNotContain(DOUALA_NO_COORDS, NEAR_5KM);
    }

    @Test
    @DisplayName("livreur sans position, ville connue : toutes les annonces de sa ville, aucune autre")
    void freelancerWithoutPosition_cityOnly() {
        assertThat(cityOnly("douala"))
                .contains(NEAR_5KM, AT_10KM, AT_20KM, DOUALA_NO_COORDS)
                .doesNotContain(YAOUNDE, YAOUNDE_NO_COORDS);
    }

    @Test
    @DisplayName("livreur avec position mais ville inconnue : une annonce sans coordonnées reste visible (rien pour trancher)")
    void freelancerWithoutCity_keepsUndecidableAnnouncements() {
        assertThat(near(LAT, LNG, 15, "")).contains(NEAR_5KM, DOUALA_NO_COORDS, YAOUNDE_NO_COORDS)
                .doesNotContain(YAOUNDE, AT_20KM);
    }

    @Test
    @DisplayName("seules les annonces ouvertes du tenant : ni ASSIGNED, ni autre tenant")
    void onlyOpenAnnouncementsOfTheTenant() {
        assertThat(near(LAT, LNG, 15, "douala")).doesNotContain(ASSIGNED_NEAR, OTHER_TENANT_NEAR);
    }
}
