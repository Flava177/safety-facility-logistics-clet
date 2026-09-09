package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves {@link gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord} against
 * a real Postgres instance rather than reasoning about Hibernate's merge/dirty-checking semantics.
 *
 * <p>Two false starts already shipped and broke before this test existed: {@code @Version} on an
 * {@code @Embeddable} property, rejected by Hibernate at boot; and a pre-incremented domain version
 * handed to {@code merge()}, which fails on the very first sequential update. Both are exactly the
 * kind of mistake that looks correct on paper.
 */
@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "no db")
@SpringBootTest(properties = {"sfl.security.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
class OptimisticLockingIntegrationTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired
    private FacilitiesRepository facilities;

    @Test
    void sequential_updates_work_and_a_write_built_on_a_superseded_read_is_rejected() {
        String siteCode = "VS" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Instant now = Instant.now();
        Site created = facilities.saveSite(
                Site.create(UUID.randomUUID(), siteCode, "V1", null, "tester", now, SourceChannel.WEB, "c1"));

        Site updatedOnce = facilities.saveSite(
                created.update("V2", null, "tester", Instant.now(), SourceChannel.WEB, "c2"));
        assertThat(updatedOnce.metadata().version()).isEqualTo(1L);

        Site updatedTwice = facilities.saveSite(
                updatedOnce.update("V3", null, "tester", Instant.now(), SourceChannel.WEB, "c3"));
        assertThat(updatedTwice.metadata().version()).isEqualTo(2L);

        // A second reader that loaded the ORIGINAL (version 0) site and tries to save an update built
        // on that read, after the two updates above already happened to the same row.
        assertThatThrownBy(() -> facilities.saveSite(
                created.update("STALE", null, "tester", Instant.now(), SourceChannel.WEB, "c-stale")))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
