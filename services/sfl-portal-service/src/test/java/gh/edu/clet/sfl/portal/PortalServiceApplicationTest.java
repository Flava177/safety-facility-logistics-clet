package gh.edu.clet.sfl.portal;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Nothing else in this module was ever exercised by CI (see {@code .github/workflows/backend.yml}),
 * so this is the one test standing between a missing bean and the module silently failing to boot -
 * exactly the failure mode {@code sfl-safety-security-service} hit on 1 Aug 2026.
 */
@SpringBootTest
class PortalServiceApplicationTest {

    @Test
    void contextLoads() {
    }
}
