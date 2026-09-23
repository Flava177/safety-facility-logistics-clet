package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEventKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.MusterStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles.FakeOnSitePopulationPort;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles.InMemoryLifeSafetyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S162a-04: evacuation roll-call - open on a fire/panic event, check in, highlight outstanding. */
class MusterServiceTest {

    private static final String SITE = "E2E-HQ";
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryLifeSafetyRepository repository;
    private FakeOnSitePopulationPort population;
    private MusterService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLifeSafetyRepository();
        population = new FakeOnSitePopulationPort();
        service = new MusterService(repository, new LifeSafetyAccessPolicy(), population, clock);
    }

    private LifeSafetyEvent fireEvent() {
        return new LifeSafetyEvent(UUID.randomUUID(), SITE, "FIRE-PANEL-VENDOR", "EXT-1", "DEV-1", "ZONE-A",
                LifeSafetyEventKind.FIRE, clock.instant(),
                RecordMetadata.createdBy("integration", clock.instant(), SourceChannel.INTEGRATION, "corr-1"));
    }

    @Test
    void opening_for_the_same_zone_twice_reuses_the_existing_open_session() {
        var coordinator = LifeSafetyTestDoubles.actor("ec-1", SflRole.EMERGENCY_COORDINATOR, SITE);
        var event = fireEvent();

        var first = service.openForEvent(event, coordinator);
        var second = service.openForEvent(event, coordinator);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(first.status()).isEqualTo(MusterStatus.OPEN);
    }

    @Test
    void a_checked_in_person_is_no_longer_outstanding_but_an_unchecked_person_stays_highlighted() {
        population.seed("staff-1", "staff-2", "visitor-1");
        var coordinator = LifeSafetyTestDoubles.actor("ec-1", SflRole.EMERGENCY_COORDINATOR, SITE);
        var session = service.openForEvent(fireEvent(), coordinator);

        service.checkIn(session.id(), "staff-1", coordinator);
        service.checkIn(session.id(), "visitor-1", coordinator);
        var rollCall = service.rollCall(session.id(), coordinator);

        assertThat(rollCall.checkIns()).hasSize(2);
        assertThat(rollCall.outstanding()).containsExactly("staff-2");
    }

    @Test
    void closing_a_session_marks_it_closed() {
        var coordinator = LifeSafetyTestDoubles.actor("ec-1", SflRole.EMERGENCY_COORDINATOR, SITE);
        var session = service.openForEvent(fireEvent(), coordinator);

        var closed = service.close(session.id(), coordinator);

        assertThat(closed.status()).isEqualTo(MusterStatus.CLOSED);
    }
}
