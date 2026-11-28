package com.yowyob.tiibntick.bootstrap.gofp;

import com.yowyob.tiibntick.core.gofreelancer.adapter.in.web.request.FreelancerPricingDTO;
import com.yowyob.tiibntick.core.gofreelancer.config.GofpCourierPricingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The platform grids of the real {@code application.yml} bind to {@link GofpCourierPricingProperties} with every
 * column of decision D-058 ({@code docs/architecture/chantiers/tarifs-par-vehicule.md} §1): a yml entry replaces the
 * code default, so a missing column there would silently drop the minimum or the waiting fee.
 */
class CourierPlatformGridsBindingTest {

    private static GofpCourierPricingProperties bound() throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        sources.forEach(env.getPropertySources()::addLast);
        return new Binder(ConfigurationPropertySources.get(env))
                .bindOrCreate("tnt.gofp.courier", Bindable.of(GofpCourierPricingProperties.class));
    }

    @Test
    @DisplayName("application.yml carries the full D-058 grid of each of the 8 vehicle keys")
    void everyGridColumnIsBound() throws IOException {
        GofpCourierPricingProperties p = bound();

        assertThat(p.getGrids()).containsOnlyKeys(
                "velo", "moto", "scooter", "tricycle", "taxi", "camionnette", "camion", "bus");
        p.getGrids().forEach((key, g) -> {
            assertThat(g.getMinimumFee()).as(key + " minimum").isNotNull();
            assertThat(g.getWaitingFeePerMinute()).as(key + " waiting").isNotNull();
            assertThat(g.getPricePerCbm()).as(key + " m3").isNotNull();
        });

        assertGrid(p.gridForVehicle("BIKE"), 300, 75, 50, 500, 15);
        assertGrid(p.gridForVehicle("MOTORBIKE"), 600, 100, 75, 1000, 25);
        assertGrid(p.gridForVehicle("SCOOTER"), 500, 90, 75, 900, 25);
        assertGrid(p.gridForVehicle("TRICYCLE"), 1000, 150, 15, 1500, 25);
        assertGrid(p.gridForVehicle("CAR"), 1000, 200, 25, 2000, 50);
        assertGrid(p.gridForVehicle("VAN"), 2500, 300, 10, 5000, 75);
        assertGrid(p.gridForVehicle("TRUCK"), 5000, 400, 5, 10000, 100);
        assertGrid(p.gridForVehicle("BUS"), 1000, 10, 100, 1500, 0);
        assertThat(p.gridForVehicle("CAR").getFragileSurcharge()).isEqualTo(750.0);
        assertThat(p.gridForVehicle("TRUCK").getPricePerCbm()).isEqualTo(500.0);
    }

    private static void assertGrid(FreelancerPricingDTO g, double base, double km, double kg, double min,
                                   double waiting) {
        assertThat(g.getBaseFee()).isEqualTo(base);
        assertThat(g.getPricePerKm()).isEqualTo(km);
        assertThat(g.getPricePerKg()).isEqualTo(kg);
        assertThat(g.getMinimumFee()).isEqualTo(min);
        assertThat(g.getWaitingFeePerMinute()).isEqualTo(waiting);
        assertThat(g.getCurrency()).isEqualTo("XAF");
    }
}
