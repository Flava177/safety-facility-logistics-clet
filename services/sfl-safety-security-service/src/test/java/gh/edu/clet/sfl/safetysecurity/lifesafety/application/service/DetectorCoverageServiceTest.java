package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.TestResult;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles;
import gh.edu.clet.sfl.safetysecurity.lifesafety.support.LifeSafetyTestDoubles.InMemoryLifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingAuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.support.PlatformTestDoubles.RecordingEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** SRS-SFL-S162a-05 (SHOULD): a failed functional test flags the device and raises a compliance exception. */
class DetectorCoverageServiceTest {

    private static final String SITE = "E2E-HQ";
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T09:00:00Z"), ZoneOffset.UTC);

    private InMemoryLifeSafetyRepository repository;
    private DetectorCoverageService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryLifeSafetyRepository();
        service = new DetectorCoverageService(repository, new LifeSafetyAccessPolicy(), new RecordingAuditPort(),
                new RecordingEventPublisher(), clock);
    }

    @Test
    void a_failed_functional_test_raises_a_compliance_exception() {
        var admin = LifeSafetyTestDoubles.actor("hse-1", SflRole.HSE_MANAGER, SITE);
        var device = service.register(new DetectorCoverageService.RegisterDevice(SITE, "ZONE-A", "DET-1",
                DetectorType.SMOKE_DETECTOR, 90, admin));

        var updated = service.recordTest(device.id(), TestResult.FAIL, 90, admin);

        assertThat(updated.lastTestResult()).isEqualTo(TestResult.FAIL);
        var exceptions = repository.findComplianceExceptions(SITE, ComplianceExceptionStatus.OPEN);
        assertThat(exceptions).hasSize(1);
        assertThat(exceptions.get(0).kind()).isEqualTo(ComplianceExceptionKind.FAILED_TEST);
    }

    @Test
    void a_passing_test_raises_no_exception() {
        var admin = LifeSafetyTestDoubles.actor("hse-1", SflRole.HSE_MANAGER, SITE);
        var device = service.register(new DetectorCoverageService.RegisterDevice(SITE, "ZONE-A", "DET-2",
                DetectorType.PANIC_DEVICE, 90, admin));

        service.recordTest(device.id(), TestResult.PASS, 90, admin);

        assertThat(repository.findComplianceExceptions(SITE, ComplianceExceptionStatus.OPEN)).isEmpty();
    }
}
