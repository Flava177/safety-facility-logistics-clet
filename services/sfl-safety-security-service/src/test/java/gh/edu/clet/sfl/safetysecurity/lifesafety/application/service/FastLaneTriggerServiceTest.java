package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.FastLaneStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEvent;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyEventKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles.FakeEmergencyFastLanePort;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles.InMemoryLifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingAuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S162a-02: the fast-lane trigger records timing and status regardless of outcome. */
class FastLaneTriggerServiceTest {

    private static final String SITE = "E2E-HQ";
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryLifeSafetyRepository repository;
    private RecordingAuditPort audit;
    private RecordingEventPublisher events;
    private FakeEmergencyFastLanePort emergency;
    private FastLaneTriggerService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLifeSafetyRepository();
        audit = new RecordingAuditPort();
        events = new RecordingEventPublisher();
        emergency = new FakeEmergencyFastLanePort();
        service = new FastLaneTriggerService(repository, new LifeSafetyAccessPolicy(), audit, events, emergency,
                clock);
    }

    private LifeSafetyEvent fireEvent() {
        return new LifeSafetyEvent(UUID.randomUUID(), SITE, "FIRE-PANEL-VENDOR", "EXT-1", "DEV-1", "ZONE-A",
                LifeSafetyEventKind.FIRE, clock.instant(),
                RecordMetadata.createdBy("integration", clock.instant(), SourceChannel.INTEGRATION, "corr-1"));
    }

    @Test
    void records_a_triggered_outcome_with_latency_when_the_port_activates() {
        var trigger = service.trigger(fireEvent(),
                LifeSafetyTestDoubles.actor("soc-1", SflRole.SOC_OPERATOR, SITE));

        assertThat(trigger.status()).isEqualTo(FastLaneStatus.TRIGGERED);
        assertThat(trigger.activationId()).isNotNull();
        assertThat(trigger.latencyMillis()).isGreaterThanOrEqualTo(0);
        assertThat(events.hasEvent("sfl.ssemp.lifesafety-fast-lane-triggered.v1")).isTrue();
        assertThat(audit.hasRecord("LIFESAFETY_FAST_LANE_TRIGGERED", "FastLaneTrigger")).isTrue();
    }

    @Test
    void records_a_degraded_outcome_when_no_break_glass_template_is_configured() {
        emergency.degradeNextCall();

        var trigger = service.trigger(fireEvent(),
                LifeSafetyTestDoubles.actor("soc-1", SflRole.SOC_OPERATOR, SITE));

        assertThat(trigger.status()).isEqualTo(FastLaneStatus.DEGRADED);
        assertThat(trigger.activationId()).isNull();
        assertThat(trigger.note()).isNotBlank();
    }
}
