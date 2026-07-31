package gh.edu.clet.sfl.facilities;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The IFIMP service: S152 CAFM/IWMS, S153 CMMS, and S159 when it arrives.
 *
 * <p>{@code @EnableScheduling} is here for S153. SRS-SFL-S153-02 requires escalation to happen "when
 * the scheduled evaluation runs", and preventive maintenance has to raise its own work — neither is
 * something anybody asks for. Both jobs live in
 * {@code maintenance.infrastructure.scheduling.MaintenanceScheduledJobs} and can be turned off with
 * {@code sfl.maintenance.scheduling.enabled=false}, which is what a test wants.
 */
@SpringBootApplication
@EnableScheduling
public class FacilitiesServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FacilitiesServiceApplication.class, args);
    }
}
