package gh.edu.clet.sfl.safetysecurity.lifesafety.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.LifeSafetyRepository;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.event.LifeSafetyEventType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.exception.LifeSafetyException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionKind;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceExceptionStatus;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.ComplianceRefType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorCoverage;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.DetectorType;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.LifeSafetyComplianceException;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.SourceChannel;
import gh.edu.clet.sfl.safetysecurity.lifesafety.domain.model.TestResult;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.AuditPort;
import gh.edu.clet.sfl.safetysecurity.platform.application.port.IntegrationEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** SRS-SFL-S162a-05 (SHOULD): detector/panic-device/siren coverage per zone and functional-test tracking. */
@Service
public class DetectorCoverageService {

    private final LifeSafetyRepository repository;
    private final LifeSafetyAccessPolicy access;
    private final AuditPort audit;
    private final IntegrationEventPublisher events;
    private final Clock clock;

    public DetectorCoverageService(LifeSafetyRepository repository, LifeSafetyAccessPolicy access, AuditPort audit,
            IntegrationEventPublisher events, Clock clock) {
        this.repository = repository;
        this.access = access;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record RegisterDevice(String siteCode, String zoneCode, String deviceRef, DetectorType deviceType,
            int testFrequencyDays, ActorContext actor) {
    }

    @Transactional
    public DetectorCoverage register(RegisterDevice c) {
        access.require(c.actor(), SflPermission.LIFESAFETY_COVERAGE_MANAGE, c.siteCode(), "DetectorCoverage", null);
        var now = clock.instant();
        var coverage = new DetectorCoverage(UUID.randomUUID(), c.siteCode(), c.zoneCode(), c.deviceRef(),
                c.deviceType(), true, null, now.plusSeconds(c.testFrequencyDays() * 86400L), TestResult.NOT_TESTED,
                RecordMetadata.createdBy(c.actor().actorId(), now, SourceChannel.WEB, c.actor().correlationId()));
        return repository.saveDetectorCoverage(coverage);
    }

    @Transactional
    public DetectorCoverage recordTest(UUID id, TestResult result, int nextTestFrequencyDays, ActorContext actor) {
        var coverage = repository.findDetectorCoverage(id)
                .orElseThrow(() -> LifeSafetyException.notFound("DetectorCoverage", id));
        access.require(actor, SflPermission.LIFESAFETY_COVERAGE_MANAGE, coverage.siteCode(), "DetectorCoverage",
                id.toString());
        var now = clock.instant();
        var updated = new DetectorCoverage(coverage.id(), coverage.siteCode(), coverage.zoneCode(),
                coverage.deviceRef(), coverage.deviceType(), coverage.covered(), now,
                now.plusSeconds(nextTestFrequencyDays * 86400L), result,
                coverage.metadata().modifiedBy(actor.actorId(), now, SourceChannel.WEB, actor.correlationId()));
        var saved = repository.saveDetectorCoverage(updated);
        if (saved.flagged(now)) {
            raiseCoverageException(saved, result == TestResult.FAIL
                    ? ComplianceExceptionKind.FAILED_TEST : ComplianceExceptionKind.COVERAGE_GAP, actor);
        }
        return saved;
    }

    @Transactional
    public void sweepCoverageGaps(String siteCode, ActorContext systemActor) {
        Instant now = clock.instant();
        for (DetectorCoverage coverage : repository.findDetectorCoverages(siteCode)) {
            if (coverage.flagged(now) && !repository.hasOpenComplianceException(siteCode, coverage.id(),
                    ComplianceExceptionKind.COVERAGE_GAP)) {
                raiseCoverageException(coverage, ComplianceExceptionKind.COVERAGE_GAP, systemActor);
            }
        }
    }

    private LifeSafetyComplianceException raiseCoverageException(DetectorCoverage coverage,
            ComplianceExceptionKind kind, ActorContext actor) {
        var now = clock.instant();
        var exception = new LifeSafetyComplianceException(UUID.randomUUID(), coverage.siteCode(),
                ComplianceRefType.DETECTOR, coverage.id(), kind, ComplianceExceptionStatus.OPEN,
                "Detector " + coverage.deviceRef() + " at zone " + coverage.zoneCode() + " flagged: " + kind, now,
                null, null, RecordMetadata.createdBy(actor.actorId(), now, SourceChannel.SYSTEM,
                        actor.correlationId()));
        var saved = repository.saveComplianceException(exception);
        audit.record(actor, SourceChannel.SYSTEM.name(), coverage.siteCode(),
                "LIFESAFETY_COMPLIANCE_EXCEPTION_RAISED", "LifeSafetyComplianceException", saved.id().toString(),
                null, saved, null);
        events.publish(LifeSafetyEventType.LIFESAFETY_COMPLIANCE_EXCEPTION_RAISED.eventType(),
                LifeSafetyEventType.LIFESAFETY_COMPLIANCE_EXCEPTION_RAISED.version(),
                "LifeSafetyComplianceException", saved.id().toString(), coverage.siteCode(), actor,
                Map.of("exceptionId", saved.id().toString(), "kind", saved.kind(), "deviceRef",
                        coverage.deviceRef()));
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DetectorCoverage> list(String siteCode, ActorContext actor) {
        access.require(actor, SflPermission.LIFESAFETY_COVERAGE_READ, siteCode, "DetectorCoverage", null);
        return repository.findDetectorCoverages(siteCode);
    }
}
