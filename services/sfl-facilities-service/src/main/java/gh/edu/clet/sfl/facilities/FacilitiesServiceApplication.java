package gh.edu.clet.sfl.facilities;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The IFIMP service: S152 CAFM/IWMS, S153 CMMS and S159 room and resource booking.
 *
 * <p>{@code @EnableScheduling} covers four jobs across two modules, and every one of them exists
 * because the SRS asks for something to happen that nobody will ask for:
 *
 * <ul>
 *   <li>S153 escalation — SRS-SFL-S153-02 requires it "when the scheduled evaluation runs".</li>
 *   <li>S153 preventive generation — a schedule has to raise its own work.</li>
 *   <li>S159 readiness reconciliation — a hall blocked today must flag the bookings it already
 *       has, and clear them again when it is repaired.</li>
 *   <li>S159 no-shows — a booking nobody took up has to release the space by itself, or the hall
 *       stays held by somebody who never came.</li>
 * </ul>
 *
 * <p>They live in each module's {@code infrastructure.scheduling} package and are turned off with
 * {@code sfl.maintenance.scheduling.enabled=false} and {@code sfl.booking.scheduling.enabled=false},
 * which is what a test wants.
 */
@SpringBootApplication
@EnableScheduling
public class FacilitiesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FacilitiesServiceApplication.class, args);
    }
}
