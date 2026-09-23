package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles.InMemoryLifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingAuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S162a-03: overdue inspections and panel faults raise compliance exceptions. */
class InspectionComplianceServiceTest {

    private static final String SITE = "E2E-HQ";
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryLifeSafetyRepository repository;
    private InspectionComplianceService service;
    private RecordingEventPublisher events;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLifeSafetyRepository();
        events = new RecordingEventPublisher();
        service = new InspectionComplianceService(repository, new LifeSafetyAccessPolicy(), new RecordingAuditPort(),
                events, clock);
    }

    @Test
    void sweep_raises_a_compliance_exception_for_an_overdue_schedule_and_publishes_the_event() {
        var admin = LifeSafetyTestDoubles.actor("hse-1", SflRole.HSE_MANAGER, SITE);
        var schedule = service.register(new InspectionComplianceService.RegisterSchedule(SITE, "PANEL-1",
                "Annual fire panel service", 30, admin));
        // Force it overdue by advancing the sweep's "now" past the schedule's next-due date.
        var system = LifeSafetyTestDoubles.actor("system", SflRole.SFL_ADMIN, SITE);
        Clock later = Clock.fixed(clock.instant().plusSeconds(31L * 86400), ZoneOffset.UTC);
        var laterService = new InspectionComplianceService(repository, new LifeSafetyAccessPolicy(),
                new RecordingAuditPort(), events, later);

        laterService.sweepOverdueInspections(SITE, system);

        var exceptions = repository.findComplianceExceptions(SITE, ComplianceExceptionStatus.OPEN);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0).kind()).isEqualTo(ComplianceExceptionKind.OVERDUE_INSPECTION);
        assertThat(exceptions.get(0).refId()).isEqualTo(schedule.id());
        assertThat(events.hasEvent("sfl.ssemp.lifesafety-compliance-exception-raised.v1")).isTrue();
    }

    @Test
    void sweep_does_not_duplicate_an_already_open_exception_for_the_same_schedule() {
        var admin = LifeSafetyTestDoubles.actor("hse-1", SflRole.HSE_MANAGER, SITE);
        service.register(new InspectionComplianceService.RegisterSchedule(SITE, "PANEL-2", "Quarterly test", 30,
                admin));
        Clock later = Clock.fixed(clock.instant().plusSeconds(31L * 86400), ZoneOffset.UTC);
        var laterService = new InspectionComplianceService(repository, new LifeSafetyAccessPolicy(),
                new RecordingAuditPort(), events, later);
        var system = LifeSafetyTestDoubles.actor("system", SflRole.SFL_ADMIN, SITE);

        laterService.sweepOverdueInspections(SITE, system);
        laterService.sweepOverdueInspections(SITE, system);

        assertThat(repository.findComplianceExceptions(SITE, ComplianceExceptionStatus.OPEN)).hasSize(1);
    }

    @Test
    void reports_a_panel_fault_as_an_open_compliance_exception() {
        var admin = LifeSafetyTestDoubles.actor("hse-1", SflRole.HSE_MANAGER, SITE);

        var exception = service.reportPanelFault(SITE, "PANEL-3", "Ground fault detected", admin);

        assertThat(exception.kind()).isEqualTo(ComplianceExceptionKind.PANEL_FAULT);
        assertThat(exception.status()).isEqualTo(ComplianceExceptionStatus.OPEN);
    }

    @Test
    void resolving_an_exception_moves_it_out_of_the_open_list() {
        var admin = LifeSafetyTestDoubles.actor("hse-1", SflRole.HSE_MANAGER, SITE);
        var exception = service.reportPanelFault(SITE, "PANEL-4", null, admin);

        service.resolve(exception.id(), admin);

        assertThat(repository.findComplianceExceptions(SITE, ComplianceExceptionStatus.OPEN)).isEmpty();
        assertThat(repository.findComplianceException(exception.id()).orElseThrow().status())
                .isEqualTo(ComplianceExceptionStatus.RESOLVED);
    }
}
