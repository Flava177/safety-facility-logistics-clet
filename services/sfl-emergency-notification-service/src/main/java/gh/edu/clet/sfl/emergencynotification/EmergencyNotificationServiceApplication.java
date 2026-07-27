package gh.edu.clet.sfl.emergencynotification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * S174 Emergency Mass Notification System — an independently deployable SFL.SSEMP / Emergency
 * Communications microservice. See docs/adr/0004-s174-emergency-notification-as-separate-service.md.
 */
@SpringBootApplication
@EnableScheduling
public class EmergencyNotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmergencyNotificationServiceApplication.class, args);
    }
}
