package gh.edu.clet.sfl.fleetlogistics.fleet.application.service;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.BindDriverPrincipalCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.RegisterDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.command.UpdateDriverCommand;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.AuditPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.DriverProfileRepository;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.HrmsDriverDirectoryPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IdempotencyPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.IntegrationEventPublisher;
import gh.edu.clet.sfl.fleetlogistics.fleet.application.port.RuntimeConfigurationPort;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.event.FleetEventType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.DuplicateActiveIdentifierException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.OptimisticLockConflictException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.AuditAction;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DriverProfileReference;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.EligibilityAssessment;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceDetails;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.DriverEligibilityPolicy;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy.DriverLifecyclePolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Write use cases for driver profile references (SRS-SFL-S166-01).
 *
 * <p>Eligibility is recomputed on every write and after every lifecycle change, so the stored status
 * is always the conclusion of the current facts rather than something an operator typed in.
 */
@Service
public class DriverApplicationService {

    private static final String RESOURCE_TYPE = "DriverProfileReference";

    private final DriverProfileRepository drivers;
    private final HrmsDriverDirectoryPort hrmsDirectory;
    private final FleetAccessPolicy accessPolicy;
    private final AuditPort auditPort;
    private final IntegrationEventPublisher eventPublisher;
    private final IdempotencyPort idempotency;
    private final RuntimeConfigurationPort runtimeConfiguration;
    private final Clock clock;

    public DriverApplicationService(DriverProfileRepository drivers, HrmsDriverDirectoryPort hrmsDirectory,
            FleetAccessPolicy accessPolicy, AuditPort auditPort, IntegrationEventPublisher eventPublisher,
            IdempotencyPort idempotency, RuntimeConfigurationPort runtimeConfiguration, Clock clock) {
        this.drivers = drivers;
        this.hrmsDirectory = hrmsDirectory;
        this.accessPolicy = accessPolicy;
        this.auditPort = auditPort;
        this.eventPublisher = eventPublisher;
        this.idempotency = idempotency;
        this.runtimeConfiguration = runtimeConfiguration;
        this.clock = clock;
    }

    /** SRS-SFL-S166-01: register a driver profile reference, verified against the HRMS directory. */
    @Transactional
    public DriverProfileReference register(RegisterDriverCommand command) {
        SiteCode site = SiteCode.of(command.siteCode());
        accessPolicy.require(command.actor(), SflPermission.FLEET_DRIVER_MANAGE, site, RESOURCE_TYPE, null);

        String fingerprint = idempotency.fingerprint(command.idempotencyPayload());
        Optional<UUID> replayed = idempotency.findExistingResult("register-driver", command.idempotencyKey(),
                fingerprint);
        if (replayed.isPresent()) {
            return drivers.findById(replayed.get())
                    .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, replayed.get()));
        }

        requireUniqueStaffReference(site, command.staffReference());
        requireUniqueLicenceNumber(site, command.licenceNumber());

        // The HRMS lookup is a check, not a copy: it confirms the staff reference exists and is
        // employed. If HRMS is unreachable the adapter fails loudly rather than assuming.
        hrmsDirectory.requireEmployedStaff(command.staffReference(), site.value());

        Instant now = clock.instant();
        DriverProfileReference driver = DriverProfileReference.register(
                UUID.randomUUID(),
                command.staffReference(),
                command.displayName(),
                new LicenceDetails(command.licenceNumber(), command.licenceClass(), command.licenceExpiresOn()),
                command.medicalClearanceExpiresOn(),
                site,
                command.responsibleUnit(),
                // The identity that will sign in as this driver, if it is known at registration. Null
                // is the ordinary case — most driver references exist for people who never open SFL —
                // and an unbound profile is assignable but shows its holder no trips of their own.
                command.principalSubject(),
                RecordMetadata.createdBy(command.actor().actorId(), now, command.sourceChannel(),
                        command.actor().correlationId()));

        EligibilityAssessment assessment = DriverEligibilityPolicy.assessGeneral(driver, now,
                runtimeConfiguration.complianceExpiryWarningWindow(site.value()));
        DriverProfileReference saved = drivers.save(driver.withEligibility(assessment.status(), driver.metadata()));

        auditPort.record(command.actor(), command.sourceChannel(), site, AuditAction.CREATE, RESOURCE_TYPE,
                saved.id().toString(), null, auditImage(saved));
        eventPublisher.publish(FleetEventType.DRIVER_REGISTERED, RESOURCE_TYPE, saved.id().toString(), site,
                command.actor(), Map.of(
                        "driverId", saved.id().toString(),
                        "staffReference", saved.staffReference(),
                        "siteCode", site.value(),
                        "eligibilityStatus", saved.eligibilityStatus().name()));
        idempotency.recordResult("register-driver", command.idempotencyKey(), fingerprint, saved.id(),
                site.value(), command.actor().actorId());
        return saved;
    }

    /** SRS-SFL-S166-01: update a driver profile and, where requested, its lifecycle status. */
    @Transactional
    public DriverProfileReference update(UpdateDriverCommand command) {
        DriverProfileReference existing = requireDriver(command.driverId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_DRIVER_MANAGE, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        if (command.targetLifecycleStatus() != null
                && DriverLifecyclePolicy.isPrivileged(existing.lifecycleStatus(), command.targetLifecycleStatus())) {
            accessPolicy.requirePrivilegedTransition(command.actor(), SflPermission.FLEET_WORKFLOW_APPROVE,
                    existing.siteCode(), RESOURCE_TYPE, existing.id().toString());
        }

        Instant now = clock.instant();
        RecordMetadata metadata = existing.metadata().modifiedBy(command.actor().actorId(), now,
                command.sourceChannel(), command.actor().correlationId());

        LicenceDetails licence = new LicenceDetails(command.licenceNumber(), command.licenceClass(),
                command.licenceExpiresOn());
        if (!licence.number().equals(existing.licence().number())) {
            requireUniqueLicenceNumber(existing.siteCode(), licence.number());
        }

        DriverProfileReference updated = existing.updateDetails(command.displayName(), licence,
                command.medicalClearanceExpiresOn(), command.responsibleUnit(), metadata);
        if (command.targetLifecycleStatus() != null
                && command.targetLifecycleStatus() != existing.lifecycleStatus()) {
            updated = updated.changeLifecycle(command.targetLifecycleStatus(), command.lifecycleReason(), metadata);
        }

        EligibilityAssessment assessment = DriverEligibilityPolicy.assessGeneral(updated, now,
                runtimeConfiguration.complianceExpiryWarningWindow(updated.siteCode().value()));
        DriverProfileReference saved = drivers.save(updated.withEligibility(assessment.status(), metadata));

        auditPort.record(command.actor(), command.sourceChannel(), saved.siteCode(), AuditAction.UPDATE,
                RESOURCE_TYPE, saved.id().toString(), auditImage(existing), auditImage(saved));
        if (existing.eligibilityStatus() != saved.eligibilityStatus()) {
            publishEligibilityChanged(command, existing, saved, assessment);
        }
        return saved;
    }

    /**
     * SRS-SFL-S166-01: links this driver profile to the identity that signs in as it.
     *
     * <h2>Why this exists at all</h2>
     *
     * <p>A driver's trip list is narrowed to the trips assigned to them, and until this binding is set
     * the platform has no way to know which driver a signed-in person <em>is</em>. It used to guess, by
     * comparing the driver's staff reference against the token's subject claim; that guess is always
     * wrong under real authentication. See {@code DriverScopeResolver}.
     *
     * <h2>Two refusals worth noting</h2>
     *
     * <p>Binding a subject already bound to another profile is refused rather than moved, because a
     * silent move revokes the first driver's access to their own records with nothing to show for it.
     * The duplicate index enforces the same rule at the database, and this check exists to give the
     * caller a usable error instead of a constraint violation.
     *
     * <p>A null or blank subject unbinds, which is the supported way to revoke access when somebody
     * leaves. It is not an error — an unbound profile is still assignable, it just shows nobody
     * anything.
     */
    @Transactional
    public DriverProfileReference bindPrincipal(BindDriverPrincipalCommand command) {
        DriverProfileReference existing = requireDriver(command.driverId());
        accessPolicy.require(command.actor(), SflPermission.FLEET_DRIVER_MANAGE, existing.siteCode(),
                RESOURCE_TYPE, existing.id().toString());
        requireExpectedVersion(existing, command.expectedVersion());

        String subject = command.principalSubject() == null || command.principalSubject().isBlank()
                ? null
                : command.principalSubject().strip();

        if (subject != null) {
            drivers.findActiveByPrincipalSubject(subject)
                    .filter(bound -> !bound.id().equals(existing.id()))
                    .ifPresent(bound -> {
                        throw new DuplicateActiveIdentifierException(Map.of(
                                "resourceType", RESOURCE_TYPE,
                                "field", "principalSubject",
                                "value", subject,
                                "conflictingResourceId", bound.id().toString(),
                                "reason", "That sign-in is already linked to driver "
                                        + bound.staffReference()));
                    });
        }

        Instant now = clock.instant();
        RecordMetadata metadata = existing.metadata().modifiedBy(command.actor().actorId(), now,
                command.sourceChannel(), command.actor().correlationId());

        DriverProfileReference saved = drivers.save(existing.bindPrincipal(subject, metadata));

        auditPort.record(command.actor(), command.sourceChannel(), saved.siteCode(), AuditAction.UPDATE,
                RESOURCE_TYPE, saved.id().toString(), auditImage(existing), auditImage(saved));
        return saved;
    }

    /**
     * Recomputes eligibility for one driver, publishing an event when it changes.
     *
     * <p>Used by the scheduled expiry sweep as well as by the assignment path, so a licence that
     * lapsed overnight is caught even if nobody edits the profile.
     */
    @Transactional
    public DriverProfileReference reassessEligibility(UUID driverId, gh.edu.clet.sfl.common.security.ActorContext
            actor, gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel sourceChannel) {
        DriverProfileReference existing = requireDriver(driverId);
        Instant now = clock.instant();
        EligibilityAssessment assessment = DriverEligibilityPolicy.assessGeneral(existing, now,
                runtimeConfiguration.complianceExpiryWarningWindow(existing.siteCode().value()));
        if (!DriverEligibilityPolicy.hasChanged(existing, assessment)) {
            return existing;
        }

        RecordMetadata metadata = existing.metadata().modifiedBy(actor.actorId(), now, sourceChannel,
                actor.correlationId());
        DriverProfileReference saved = drivers.save(existing.withEligibility(assessment.status(), metadata));

        auditPort.record(actor, sourceChannel, saved.siteCode(), AuditAction.UPDATE, RESOURCE_TYPE,
                saved.id().toString(), auditImage(existing), auditImage(saved));
        eventPublisher.publish(FleetEventType.DRIVER_ELIGIBILITY_CHANGED, RESOURCE_TYPE, saved.id().toString(),
                saved.siteCode(), actor, eligibilityPayload(existing, saved, assessment));
        return saved;
    }

    private void publishEligibilityChanged(UpdateDriverCommand command, DriverProfileReference before,
            DriverProfileReference after, EligibilityAssessment assessment) {
        eventPublisher.publish(FleetEventType.DRIVER_ELIGIBILITY_CHANGED, RESOURCE_TYPE, after.id().toString(),
                after.siteCode(), command.actor(), eligibilityPayload(before, after, assessment));
    }

    private static Map<String, Object> eligibilityPayload(DriverProfileReference before,
            DriverProfileReference after, EligibilityAssessment assessment) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("driverId", after.id().toString());
        payload.put("fromStatus", before.eligibilityStatus().name());
        payload.put("toStatus", after.eligibilityStatus().name());
        payload.put("blockerCodes", assessment.codes().stream().map(Enum::name).toList());
        return payload;
    }

    private DriverProfileReference requireDriver(UUID driverId) {
        return drivers.findById(driverId)
                .orElseThrow(() -> RecordNotFoundException.of(RESOURCE_TYPE, driverId));
    }

    private void requireUniqueStaffReference(SiteCode site, String staffReference) {
        if (drivers.findActiveByStaffReference(site, staffReference).isPresent()) {
            throw DuplicateActiveIdentifierException.of(RESOURCE_TYPE, "staffReference", staffReference,
                    site.value());
        }
    }

    private void requireUniqueLicenceNumber(SiteCode site, String licenceNumber) {
        drivers.findActiveByLicenceNumber(site, licenceNumber).ifPresent(existing -> {
            // The error masks the licence number: it is a sensitive field even inside an error message.
            throw DuplicateActiveIdentifierException.of(RESOURCE_TYPE, "licenceNumber",
                    existing.licence().maskedNumber(), site.value());
        });
    }

    private static void requireExpectedVersion(DriverProfileReference driver, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != driver.metadata().version()) {
            throw new OptimisticLockConflictException(Map.of(
                    "expectedVersion", expectedVersion,
                    "currentVersion", driver.metadata().version()));
        }
    }

    /** Data-minimised audit image: the licence number is masked, never stored in full in the log. */
    static Map<String, Object> auditImage(DriverProfileReference driver) {
        Map<String, Object> image = new LinkedHashMap<>();
        image.put("driverId", driver.id().toString());
        image.put("staffReference", driver.staffReference());
        image.put("displayName", driver.displayName());
        image.put("licenceNumber", driver.licence().maskedNumber());
        image.put("licenceClass", driver.licence().licenceClass().name());
        image.put("licenceExpiresOn", driver.licence().expiresOn().toString());
        image.put("medicalClearanceExpiresOn", driver.medicalClearanceExpiresOn() == null
                ? null
                : driver.medicalClearanceExpiresOn().toString());
        image.put("siteCode", driver.siteCode().value());
        image.put("responsibleUnit", driver.responsibleUnit());
        image.put("lifecycleStatus", driver.lifecycleStatus().name());
        image.put("eligibilityStatus", driver.eligibilityStatus().name());
        image.put("suspensionReason", driver.suspensionReason());
        image.put("version", driver.metadata().version());
        return image;
    }
}
