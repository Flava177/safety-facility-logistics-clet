package gh.edu.clet.sfl.fleetlogistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SFL Fleet and Logistics service (SFL.FTLMP).
 *
 * <p>Owns the {@code fleet_logistics} schema. This slice implements S166 Fleet and Vehicle Management;
 * S168_fuel and S171 follow in later slices and reuse the same platform foundation.
 */
@SpringBootApplication
public class FleetLogisticsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FleetLogisticsServiceApplication.class, args);
    }
}
