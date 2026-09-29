package gh.edu.clet.sfl.facilities.energy.application;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import gh.edu.clet.sfl.common.security.SiteScopedPrincipal;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyDeviceDirectoryPort;
import gh.edu.clet.sfl.facilities.energy.application.ports.EnergyRepository;
import gh.edu.clet.sfl.facilities.energy.application.ports.MeteringVendorPort;
import gh.edu.clet.sfl.facilities.energy.domain.ConsumptionReading;
import gh.edu.clet.sfl.facilities.energy.domain.DailyConsumption;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyMeter;
import gh.edu.clet.sfl.facilities.energy.domain.EnergyPeriod;
import gh.edu.clet.sfl.facilities.energy.domain.MeterSource;
import gh.edu.clet.sfl.facilities.energy.domain.ReadingStatus;
import gh.edu.clet.sfl.facilities.energy.domain.Utility;
import gh.edu.clet.sfl.facilities.energy.domain.policy.PlausibilityPolicy;
import gh.edu.clet.sfl.facilities.shared.application.FacilitiesAuthorization;
import gh.edu.clet.sfl.facilities.shared.application.ServiceOutbox;
import gh.edu.clet.sfl.facilities.shared.application.port.AuditPort;
import gh.edu.clet.sfl.facilities.shared.application.port.IdempotencyPort;
import gh.edu.clet.sfl.facilities.shared.application.vendor.SignedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VendorMessageVerifier;
import gh.edu.clet.sfl.facilities.shared.application.vendor.VerifiedVendorMessage;
import gh.edu.clet.sfl.facilities.shared.domain.audit.AuditAction;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.security.VendorChannel;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Metering ingestion and the consumption record - SRS-SFL-S157-01, and the S157 half of S157-04.
 *
 * <h2>Three paths in, one record out</h2>
 *
 * <ul>
 *   <li><strong>AMI gateway</strong> ({@link #ingest}) - through {@link VendorMessageVerifier} on the
 *       {@code ENERGY_METERING} channel, then the vendor adapter. Nothing is believed before the verifier
 *       has passed it, and a message failing a check after that is rejected through the verifier too, so a
 *       forged or malformed message is always rejected <em>and logged</em> and never posted (NFR-SEC2).</li>
 *   <li><strong>S156 stream</strong> ({@link #consumeStream}) - the observer hands over S156's normalised
 *       readings; S157 opens no vendor connection of its own for those devices (S157-04).</li>
 *   <li><strong>Manual entry</strong> ({@link #enterManual}) - with the plausibility band and the
 *       entered-by / verified-by trail.</li>
 * </ul>
 *
 * <p>All three end in {@link #postToRecord}, the only place the consumption record is written, so a held
 * reading cannot reach a budget by any route that forgot to check.
 */
@Service
public class EnergyReadingService {

    static final String MODULE = "S157";
    private static final String RESOURCE = "EnergyReading";

    /**
     * The actor stream readings are recorded against. S156 calls the observer with no actor of its own; the
     * stream is trusted because S156 authenticated it, and the audit trail should say a service did it.
     */
    private static final SiteScopedPrincipal STREAM = new SiteScopedPrincipal("system.energy-stream",
            "S157 energy stream consumer", Set.of(SflRole.SFL_ADMIN), Set.of("*"), true);

    private final EnergyRepository repository;
    private final EnergyMeterService meters;
    private final MeteringVendorPort vendor;
    private final EnergyDeviceDirectoryPort devices;
    private final VendorMessageVerifier verifier;
    private final EnergyConfiguration configuration;
    private final FacilitiesAuthorization authorization;
    private final AuditPort audit;
    private final IdempotencyPort idempotency;
    private final ServiceOutbox outbox;
    private final Clock clock;

    public EnergyReadingService(EnergyRepository repository, EnergyMeterService meters, MeteringVendorPort vendor,
            EnergyDeviceDirectoryPort devices, VendorMessageVerifier verifier, EnergyConfiguration configuration,
            FacilitiesAuthorization authorization, AuditPort audit, IdempotencyPort idempotency, ServiceOutbox outbox,
            Clock clock) {
        this.repository = repository;
        this.meters = meters;
        this.vendor = vendor;
        this.devices = devices;
        this.verifier = verifier;
        this.configuration = configuration;
        this.authorization = authorization;
        this.audit = audit;
        this.idempotency = idempotency;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** What an AMI message produced. {@code duplicate} answers a repeat exactly as the first time. */
    public record IngestResult(ConsumptionReading reading, boolean duplicate) {
    }

    /** What the S156 stream observer did with a reading. Only POSTED writes anything to the record. */
    public enum StreamOutcome {
        POSTED,
        DUPLICATE,
        NOT_A_METER,
        REFUSED
    }

    // =============================================================================================
    // AMI gateway - S157-01 "via vendor API/AMI gateway behind the integration boundary"
    // =============================================================================================

    @Transactional
    public IngestResult ingest(SignedVendorMessage message, ActorContext actor) {
        // Permission before verification: a person holding a stolen gateway secret is still not an
        // integration principal, and is refused without the payload being read.
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READING_INGEST, SourceChannel.INTEGRATION,
                RESOURCE, "ingest", message.siteCode());
        VerifiedVendorMessage verified = verifier.accept(message, VendorChannel.ENERGY_METERING,
                vendor.requiredFields(), MODULE, actor);
        String sourceReference = verified.sourceId() + ":" + verified.idempotencyKey();
        if (verified.duplicate()) {
            return new IngestResult(repository.findReadingBySourceReference(sourceReference).orElse(null), true);
        }

        EnergyMeter meter = resolveAmiMeter(verified, actor);
        MeteringVendorPort.MeterInterval interval;
        try {
            interval = vendor.translate(verified.payload(), meter.utility());
        } catch (MeteringVendorPort.UntranslatableMessageException untranslatable) {
            throw verifier.reject(verified, untranslatable.getMessage(), MODULE, actor);
        }
        if (interval.intervalEnd().isAfter(clock.instant().plus(Duration.ofMinutes(5)))) {
            throw verifier.reject(verified, "Interval ends in the future.", MODULE, actor);
        }

        Instant at = clock.instant();
        ConsumptionReading reading = repository.saveReading(ConsumptionReading.interval(UUID.randomUUID(), meter,
                interval.intervalStart(), interval.intervalEnd(), interval.consumption(), interval.vendorValue(),
                interval.vendorUnit(), sourceReference, actor.actorId(), at, SourceChannel.INTEGRATION,
                actor.correlationId()));
        postToRecord(reading, meter, actor, SourceChannel.INTEGRATION, at);
        audit.record(actor, SourceChannel.INTEGRATION, AuditAction.ENERGY_READING_INGESTED, RESOURCE,
                reading.id().toString(), reading.siteCode(), null, reading);
        return new IngestResult(reading, false);
    }

    /**
     * The meter a verified AMI message names, or a rejection. Every refusal here is a message that passed
     * authentication and still must not be actioned, so each goes through {@link VendorMessageVerifier#reject}.
     */
    private EnergyMeter resolveAmiMeter(VerifiedVendorMessage verified, ActorContext actor) {
        String reference;
        try {
            reference = vendor.meterReference(verified.payload());
        } catch (MeteringVendorPort.UntranslatableMessageException untranslatable) {
            throw verifier.reject(verified, untranslatable.getMessage(), MODULE, actor);
        }
        EnergyMeter meter = repository.findMeterByVendorRef(reference)
                .orElseThrow(() -> verifier.reject(verified, "No meter has vendor reference " + reference + ".",
                        MODULE, actor));
        if (!meter.siteCode().equals(verified.siteCode())) {
            throw verifier.reject(verified, "Meter " + meter.meterCode() + " is registered at " + meter.siteCode()
                    + ", not " + verified.siteCode() + ".", MODULE, actor);
        }
        if (!meter.isActive()) {
            throw verifier.reject(verified, "Meter " + meter.meterCode() + " is retired.", MODULE, actor);
        }
        if (meter.source() != MeterSource.AMI) {
            throw verifier.reject(verified, "Meter " + meter.meterCode() + " is " + meter.source()
                    + "; only AMI meters accept gateway readings (S157-04).", MODULE, actor);
        }
        if (meter.avampAssetId() != null && devices.findByAvampAssetId(meter.avampAssetId()).isPresent()) {
            // The device has since been enrolled in S156. Accepting would keep two ingestion boundaries open
            // for one device, which S157-04 forbids; the health view lists the meter so it can be moved.
            throw verifier.reject(verified, "Meter " + meter.meterCode() + " (AVAMP " + meter.avampAssetId()
                    + ") is now ingested by S156; move it to BMS_STREAM rather than keeping a second vendor "
                    + "connection (S157-04).", MODULE, actor);
        }
        return meter;
    }

    // =============================================================================================
    // S156 stream - S157-04 "consumes the existing normalised stream rather than opening a second vendor
    // connection"
    // =============================================================================================

    /**
     * Turns one energy-relevant S156 reading into a consumption reading, when it belongs to a BMS_STREAM meter.
     *
     * <p>Runs in S156's transaction, so it must be quick and must not throw for anything S156 could not have
     * prevented: a reading for a device that is not a meter is simply not S157's business. A reading for a
     * device registered here under another source is an S157-04 conflict and is audited as
     * {@code ENERGY_TELEMETRY_REJECTED} rather than posted - posting it would double-count a meter the AMI
     * feed is also reporting.
     */
    @Transactional
    public StreamOutcome consumeStream(EnergyCommands.StreamReading reading) {
        Optional<EnergyMeter> found = reading.avampAssetId() == null ? Optional.empty()
                : repository.findMeterByAvampAssetId(reading.avampAssetId());
        if (found.isEmpty()) {
            return StreamOutcome.NOT_A_METER;
        }
        EnergyMeter meter = found.get();
        String sourceReference = "bms:" + reading.streamReadingId();
        if (repository.findReadingBySourceReference(sourceReference).isPresent()) {
            return StreamOutcome.DUPLICATE;
        }
        ActorContext actor = new ActorContext(STREAM, reading.streamReadingId().toString());
        String refusal = streamRefusal(meter, reading);
        if (refusal != null) {
            audit.record(actor, SourceChannel.INTEGRATION, AuditAction.ENERGY_TELEMETRY_REJECTED, RESOURCE,
                    reading.streamReadingId().toString(), meter.siteCode(), null,
                    Map.of("meterId", meter.id().toString(), "reason", refusal));
            return StreamOutcome.REFUSED;
        }
        Instant at = clock.instant();
        Instant intervalStart = repository.findLatestPostedReading(meter.id()).map(ConsumptionReading::observedAt)
                .filter(previous -> previous.isBefore(reading.observedAt())).orElse(null);
        ConsumptionReading posted = repository.saveReading(ConsumptionReading.interval(UUID.randomUUID(), meter,
                intervalStart, reading.observedAt(), reading.value().setScale(4, RoundingMode.HALF_UP), reading.value(),
                meter.utility().unitCode(), sourceReference, actor.actorId(), at, SourceChannel.INTEGRATION,
                actor.correlationId()));
        postToRecord(posted, meter, actor, SourceChannel.INTEGRATION, at);
        audit.record(actor, SourceChannel.INTEGRATION, AuditAction.ENERGY_READING_INGESTED, RESOURCE,
                posted.id().toString(), posted.siteCode(), null, posted);
        return StreamOutcome.POSTED;
    }

    private static String streamRefusal(EnergyMeter meter, EnergyCommands.StreamReading reading) {
        if (meter.source() != MeterSource.BMS_STREAM) {
            return "Meter " + meter.meterCode() + " is registered as " + meter.source()
                    + " but its device is on the S156 stream (S157-04 conflict); not posted.";
        }
        if (!meter.isActive()) {
            return "Meter " + meter.meterCode() + " is retired.";
        }
        if (reading.utility() != meter.utility()) {
            return "Stream reading measures " + reading.utility() + " but meter " + meter.meterCode() + " is "
                    + meter.utility() + ".";
        }
        if (reading.siteCode() != null && !EstateCodes.normalize(reading.siteCode()).equals(meter.siteCode())) {
            return "Stream reading is for site " + reading.siteCode() + " but meter " + meter.meterCode() + " is at "
                    + meter.siteCode() + ".";
        }
        if (reading.value() == null || reading.value().signum() < 0) {
            return "Stream reading carries no or negative consumption.";
        }
        return null;
    }

    // =============================================================================================
    // Manual entry - S157-01 "Manual meter-read entry is supported for sites without smart metering, with an
    // entered-by/verified-by audit trail"
    // =============================================================================================

    /**
     * Records a register read. Returns the reading whether it posted or was held: a held reading is a
     * successful submission, and the caller learns which from {@link ConsumptionReading#status()}.
     *
     * <p>Held means held - saved, audited, published, and <em>not</em> in the consumption record. That is the
     * acceptance criterion: "held for verification rather than posted directly to the consumption record".
     */
    @Transactional
    public ConsumptionReading enterManual(EnergyCommands.EnterManualReading command) {
        EnergyMeter meter = meters.requireMeter(command.meterId());
        ActorContext actor = command.actor();
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READING_ENTER, meter.siteCode(),
                command.channel(), RESOURCE, meter.id().toString());
        if (command.idempotencyKey() != null) {
            Optional<ConsumptionReading> replayed = idempotency.findExistingResult("enter-energy-reading",
                    command.idempotencyKey(), idempotency.fingerprint(command.idempotencyPayload()))
                    .flatMap(repository::findReading);
            if (replayed.isPresent()) {
                return replayed.get();
            }
        }
        if (meter.source() != MeterSource.MANUAL) {
            throw new FacilitiesException.ValidationFailedException("Meter " + meter.meterCode() + " is fed by "
                    + meter.source() + ". Manual entry is for meters without smart metering.");
        }
        if (!meter.isActive()) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Meter " + meter.meterCode() + " is retired.");
        }
        if (command.registerValue() == null || command.registerValue().signum() < 0) {
            throw new FacilitiesException.ValidationFailedException("A register value of zero or more is required.");
        }
        Instant at = clock.instant();
        Instant readAt = command.readAt() == null ? at : command.readAt();
        if (readAt.isAfter(at.plus(Duration.ofMinutes(5)))) {
            throw new FacilitiesException.ValidationFailedException("A meter cannot be read in the future.");
        }
        Optional<ConsumptionReading> previous = repository.findLatestPostedReading(meter.id());
        if (previous.isPresent() && !readAt.isAfter(previous.get().observedAt())) {
            // Out-of-order register reads would make every later delta wrong. A late-found paper sheet is
            // a correction for a supervisor, not a new read.
            throw new FacilitiesException.ValidationFailedException("Meter " + meter.meterCode()
                    + " already has a posted read at " + previous.get().observedAt()
                    + "; a manual read must be later than the latest posted read.");
        }

        BigDecimal consumption = null;
        Instant intervalStart = null;
        PlausibilityPolicy.Outcome plausibility = PlausibilityPolicy.Outcome.baseline();
        if (previous.isPresent() && previous.get().registerValue() != null) {
            consumption = command.registerValue().subtract(previous.get().registerValue());
            intervalStart = previous.get().observedAt();
            TrailingAverage trailing = trailingDailyAverage(meter, readAt);
            plausibility = PlausibilityPolicy.evaluate(consumption, Duration.between(intervalStart, readAt),
                    trailing.average(), trailing.history(), configuration.plausibilityMinimumHistory(meter.siteCode()),
                    configuration.plausibilityBandPct(meter.siteCode()));
        }
        ConsumptionReading reading = repository.saveReading(ConsumptionReading.manual(UUID.randomUUID(), meter, readAt,
                intervalStart, command.registerValue(), consumption, plausibility, command.note(), actor.actorId(), at,
                command.channel(), actor.correlationId()));
        audit.record(actor, command.channel(), AuditAction.ENERGY_READING_ENTERED, RESOURCE, reading.id().toString(),
                reading.siteCode(), null, reading);
        if (reading.isPosted()) {
            postToRecord(reading, meter, actor, command.channel(), at);
        } else {
            audit.record(actor, command.channel(), AuditAction.ENERGY_READING_HELD, RESOURCE,
                    reading.id().toString(), reading.siteCode(), null, reading);
            outbox.record(EnergyEvents.READING_HELD, 1, RESOURCE, reading.id(), reading.siteCode(),
                    actor.correlationId(), actor.actorId(), EnergyEvents.ReadingHeld.of(reading));
        }
        if (command.idempotencyKey() != null) {
            idempotency.recordResult("enter-energy-reading", command.idempotencyKey(),
                    idempotency.fingerprint(command.idempotencyPayload()), reading.id(), reading.siteCode(),
                    actor.actorId());
        }
        return reading;
    }

    /**
     * A supervisor verifies or rejects a held reading - somebody other than the enterer, recorded as
     * verified-by (S157-01).
     *
     * <p>Refused once a later read on the meter has posted. That later read's delta was taken from the read
     * before this one, so posting this one now would count the same consumption twice; the held read is
     * superseded and the right answer is to reject it.
     */
    @Transactional
    public ConsumptionReading decide(EnergyCommands.DecideHeldReading command) {
        ConsumptionReading reading = repository.findReading(command.readingId())
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException(RESOURCE, command.readingId()));
        ActorContext actor = command.actor();
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READING_VERIFY, reading.siteCode(),
                command.channel(), RESOURCE, reading.id().toString());
        EnergyMeter meter = meters.requireMeter(reading.meterId());
        Instant at = clock.instant();
        if (!command.approve()) {
            ConsumptionReading rejected = repository.saveReading(reading.reject(actor.actorId(), command.note(), at,
                    command.channel(), actor.correlationId()));
            audit.record(actor, command.channel(), AuditAction.ENERGY_READING_REJECTED, RESOURCE,
                    reading.id().toString(), reading.siteCode(), reading, rejected);
            return rejected;
        }
        if (reading.status() == ReadingStatus.HELD && repository.findLatestPostedReading(meter.id())
                .map(latest -> latest.observedAt().isAfter(reading.observedAt())).orElse(false)) {
            throw new FacilitiesException.InvalidStateTransitionException("A later read on meter "
                    + meter.meterCode() + " has already posted; this held read is superseded. Reject it instead.");
        }
        BigDecimal consumption = command.consumption();
        if (consumption == null && reading.registerValue() != null) {
            consumption = repository.findLatestPostedReading(meter.id())
                    .filter(previous -> previous.registerValue() != null)
                    .map(previous -> reading.registerValue().subtract(previous.registerValue()))
                    .orElse(reading.consumption());
        }
        ConsumptionReading verified = repository.saveReading(reading.verify(consumption, actor.actorId(),
                command.note(), at, command.channel(), actor.correlationId()));
        postToRecord(verified, meter, actor, command.channel(), at);
        audit.record(actor, command.channel(), AuditAction.ENERGY_READING_VERIFIED, RESOURCE,
                reading.id().toString(), reading.siteCode(), reading, verified);
        outbox.record(EnergyEvents.READING_VERIFIED, 1, RESOURCE, verified.id(), verified.siteCode(),
                actor.correlationId(), actor.actorId(), EnergyEvents.ReadingVerified.of(verified));
        return verified;
    }

    // =============================================================================================
    // Queries - S157-01 "Readings are aggregated to daily/monthly consumption per utility type and site"
    // =============================================================================================

    @Transactional(readOnly = true)
    public List<ConsumptionReading> readings(EnergyRepository.ReadingQuery query, ActorContext actor,
            SourceChannel channel) {
        String site = query.siteCode() == null || query.siteCode().isBlank() ? null
                : EstateCodes.normalize(query.siteCode());
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, channel, RESOURCE, "list", site);
        authorization.requireRequestedSite(actor, site, channel, RESOURCE);
        EnergyRepository.ReadingQuery effective = new EnergyRepository.ReadingQuery(site, query.meterId(),
                query.status(), query.from(), query.to(), Math.min(Math.max(1, query.limit()), 1000));
        return authorization.filterBySite(actor, repository.findReadings(effective), ConsumptionReading::siteCode);
    }

    @Transactional(readOnly = true)
    public ConsumptionReading reading(UUID id, ActorContext actor, SourceChannel channel) {
        ConsumptionReading reading = repository.findReading(id)
                .orElseThrow(() -> new FacilitiesException.RecordNotFoundException(RESOURCE, id));
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, reading.siteCode(), channel, RESOURCE,
                id.toString());
        return reading;
    }

    /** How the consumption view is rolled up. */
    public enum Grouping {
        SITE,
        BUILDING
    }

    /**
     * @param buildingCode filled only for {@link Grouping#BUILDING}
     */
    public record ConsumptionPoint(String siteCode, String buildingCode, Utility utility, EnergyPeriod period,
            BigDecimal consumption, int readingCount) {
    }

    /** Daily or monthly consumption per site (or building) and utility, over {@code [from, to)}. */
    @Transactional(readOnly = true)
    public List<ConsumptionPoint> consumption(String siteCode, Utility utility, EnergyPeriod.PeriodType granularity,
            Grouping grouping, LocalDate from, LocalDate to, ActorContext actor, SourceChannel channel) {
        String site = siteCode == null || siteCode.isBlank() ? null : EstateCodes.normalize(siteCode);
        authorization.require(actor, SflPermission.FACILITIES_ENERGY_READ, channel, "EnergyConsumption", "list", site);
        authorization.requireRequestedSite(actor, site, channel, "EnergyConsumption");
        if (from == null || to == null || !to.isAfter(from)) {
            throw new FacilitiesException.ValidationFailedException("A consumption query needs from < to.");
        }
        if (Duration.between(from.atStartOfDay(), to.atStartOfDay()).toDays() > 1100) {
            throw new FacilitiesException.ValidationFailedException("A consumption query covers at most three years.");
        }
        List<DailyConsumption> days = authorization.filterBySite(actor, repository.findDaily(site, utility, from, to),
                DailyConsumption::siteCode);
        return aggregate(days, granularity == null ? EnergyPeriod.PeriodType.DAY : granularity,
                grouping == null ? Grouping.SITE : grouping);
    }

    static List<ConsumptionPoint> aggregate(List<DailyConsumption> days, EnergyPeriod.PeriodType granularity,
            Grouping grouping) {
        Map<String, ConsumptionPoint> points = new TreeMap<>();
        for (DailyConsumption day : days) {
            EnergyPeriod period = EnergyPeriod.containing(granularity, day.day());
            String building = grouping == Grouping.BUILDING ? day.buildingCode() : null;
            String key = day.siteCode() + "|" + building + "|" + day.utility() + "|" + period.start();
            ConsumptionPoint existing = points.get(key);
            points.put(key, existing == null
                    ? new ConsumptionPoint(day.siteCode(), building, day.utility(), period, day.consumption(),
                            day.readingCount())
                    : new ConsumptionPoint(day.siteCode(), building, day.utility(), period,
                            existing.consumption().add(day.consumption()), existing.readingCount() + day.readingCount()));
        }
        return new ArrayList<>(points.values());
    }

    /** SRS 4.2 retention sweep. Held readings are never purged. */
    @Transactional
    public int purgeExpired(ActorContext actor) {
        Instant cutoff = clock.instant().minus(Duration.ofDays(configuration.readingRetentionDays()));
        int purged = repository.purgeReadingsObservedBefore(cutoff);
        if (purged > 0) {
            audit.record(actor, SourceChannel.SCHEDULER, AuditAction.ENERGY_READING_RETENTION_PURGED, RESOURCE,
                    "retention", "*", null, Map.of("observedBefore", cutoff.toString(), "purged", purged));
        }
        return purged;
    }

    // =============================================================================================
    // Internals
    // =============================================================================================

    /** The single write into the consumption record. */
    void postToRecord(ConsumptionReading reading, EnergyMeter meter, ActorContext actor, SourceChannel channel,
            Instant at) {
        if (!reading.isPosted()) {
            throw new IllegalStateException("Only a posted reading enters the consumption record");
        }
        LocalDate day = EnergyPeriod.dayOf(reading.observedAt());
        DailyConsumption daily = repository.findDaily(meter.id(), day).orElseGet(() -> DailyConsumption.start(
                UUID.randomUUID(), meter, day, actor.actorId(), at, channel, actor.correlationId()));
        repository.saveDaily(daily.add(reading.consumption(), actor.actorId(), at, channel, actor.correlationId()));
    }

    private record TrailingAverage(BigDecimal average, int history) {
    }

    /**
     * Average daily consumption over the trailing window: total posted consumption divided by the total time
     * those readings covered. Weighted by coverage, so one short interval cannot dominate the average.
     */
    private TrailingAverage trailingDailyAverage(EnergyMeter meter, Instant readAt) {
        Instant since = readAt.minus(Duration.ofDays(configuration.plausibilityTrailingDays(meter.siteCode())));
        List<ConsumptionReading> history = repository.findPostedReadings(meter.id(), since, readAt).stream()
                .filter(reading -> reading.consumption() != null && reading.intervalStart() != null)
                .sorted(Comparator.comparing(ConsumptionReading::observedAt)).toList();
        if (history.isEmpty()) {
            return new TrailingAverage(null, 0);
        }
        BigDecimal total = BigDecimal.ZERO;
        long minutes = 0;
        for (ConsumptionReading reading : history) {
            total = total.add(reading.consumption());
            minutes += Math.max(60, Duration.between(reading.intervalStart(), reading.observedAt()).toMinutes());
        }
        return new TrailingAverage(total.multiply(BigDecimal.valueOf(1440))
                .divide(BigDecimal.valueOf(minutes), 4, RoundingMode.HALF_UP), history.size());
    }
}
