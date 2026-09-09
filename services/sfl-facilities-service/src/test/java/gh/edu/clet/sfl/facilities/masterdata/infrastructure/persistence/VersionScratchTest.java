package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport;
import gh.edu.clet.sfl.facilities.masterdata.application.ports.FacilitiesRepository;
import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIf(value = "gh.edu.clet.sfl.facilities.FacilitiesPostgresSupport#databaseAvailable",
        disabledReason = "no db")
@SpringBootTest(properties = {"sfl.security.enabled=false", "spring.jpa.hibernate.ddl-auto=validate"})
class VersionScratchTest {

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        FacilitiesPostgresSupport.datasource(registry);
    }

    @Autowired
    private FacilitiesRepository facilities;

    @Test
    void sequential_updates_work_and_stale_update_is_rejected() {
        String siteCode = "VS" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        Instant now = Instant.now();
        Site created = facilities.saveSite(
                Site.create(UUID.randomUUID(), siteCode, "V1", null, "tester", now, SourceChannel.WEB, "c1"));
        System.out.println("CREATED version=" + created.metadata().version());

        Site updatedOnce = facilities.saveSite(
                created.update("V2", null, "tester", Instant.now(), SourceChannel.WEB, "c2"));
        System.out.println("UPDATED-ONCE version=" + updatedOnce.metadata().version());
        assertThat(updatedOnce.metadata().version()).isEqualTo(1L);

        Site updatedTwice = facilities.saveSite(
                updatedOnce.update("V3", null, "tester", Instant.now(), SourceChannel.WEB, "c3"));
        System.out.println("UPDATED-TWICE version=" + updatedTwice.metadata().version());
        assertThat(updatedTwice.metadata().version()).isEqualTo(2L);

        // Simulate a second concurrent reader that loaded the ORIGINAL (version 0) site and tries to
        // save an update built from that stale read, after the two updates above already happened.
        assertThatThrownBy(() -> facilities.saveSite(
                created.update("STALE", null, "tester", Instant.now(), SourceChannel.WEB, "c-stale")))
                .isInstanceOf(OptimisticLockingFailureException.class);
        System.out.println("STALE WRITE CORRECTLY REJECTED");
    }
}
