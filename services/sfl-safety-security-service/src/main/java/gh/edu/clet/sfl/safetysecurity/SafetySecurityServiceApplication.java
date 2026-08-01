package gh.edu.clet.sfl.safetysecurity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The SSEMP deployable — S160, S160a, S161, S162, S162a and S163.
 *
 * <p><strong>None of those six systems is built.</strong> This module is a foundation: one controller,
 * one migration, and this class. It is declared scope that has not been started, not a system that
 * regressed.
 *
 * <p>Added 1 August 2026, and the reason is worth recording. Every document in this repository
 * described this module as a service that "compiles and boots" — the go-live readiness pack said so,
 * `README.md` listed it among the five deployables, and `CLAUDE.md` gave it a port. It did none of
 * those things: it had a controller and a migration and **no {@code @SpringBootApplication} class at
 * all**, so `spring-boot:run` failed with "Unable to find a suitable main class" and the module could
 * not start on any machine.
 *
 * <p>That went unnoticed because nothing ever tried. It has no tests, the compose file does not run
 * it, and CI builds it without launching it — so a module that could not boot compiled green for
 * months while four documents asserted the opposite. It was found by running all five services to
 * answer the question "can everything launch".
 *
 * <p>What this class buys is small and worth being precise about: the module now starts, applies its
 * foundation migration and answers {@code /actuator/health}. That makes it deployable and monitorable,
 * and makes the claim the documents were already making true. It does not make any of the six SSEMP
 * systems exist.
 */
@SpringBootApplication
public class SafetySecurityServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SafetySecurityServiceApplication.class, args);
    }
}
