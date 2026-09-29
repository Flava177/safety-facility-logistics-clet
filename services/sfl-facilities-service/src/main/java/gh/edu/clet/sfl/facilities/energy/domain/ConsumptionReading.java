package gh.edu.clet.sfl.facilities.energy.domain;

import gh.edu.clet.sfl.facilities.energy.domain.policy.PlausibilityPolicy;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesErrorCode;
import gh.edu.clet.sfl.facilities.shared.domain.error.FacilitiesException;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One reading, from any of the three paths, in the meter's canonical unit - SRS-SFL-S157-01.
 *
 * <p>{@code consumption} is what the reading adds to the consumption record: interval consumption for
 * AMI and the S156 stream, the register delta for a manual read (see {@link MeterSource}). It is
 * {@code null} for a manual baseline read - the first register read on a meter, which has nothing to be
 * a delta from - and that null is deliberate: zero would claim the building used nothing.
 *
 * @param intervalStart the start of the interval the consumption covers - the vendor's interval start,
 *        the previous stream reading, or the previous posted register read
 * @param vendorValue the number the vendor sent, before conversion, with {@code vendorUnit}
 * @param sourceReference the idempotency identity: S156's reading id, or {@code sourceId:idempotencyKey}
 *        for an AMI message; {@code null} for a manual entry, which is idempotent through the request key
 */
public record ConsumptionReading(
        UUID id,
        String siteCode,
        UUID meterId,
        String buildingCode,
        Utility utility,
        MeterSource source,
        ReadingStatus status,
        Instant observedAt,
        Instant intervalStart,
        BigDecimal registerValue,
        BigDecimal consumption,
        BigDecimal vendorValue,
        String vendorUnit,
        String sourceReference,
        boolean plausibilityChecked,
        BigDecimal trailingDailyAverage,
        BigDecimal bandLow,
        BigDecimal bandHigh,
        String holdReason,
        String enteredBy,
        Instant enteredAt,
        String verifiedBy,
        Instant verifiedAt,
        String verificationNote,
        String note,
        RecordMetadata metadata) {

    public ConsumptionReading {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(meterId, "meterId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(observedAt, "observedAt");
        if (consumption != null && consumption.signum() < 0 && status == ReadingStatus.POSTED) {
            throw new FacilitiesException.ValidationFailedException(
                    "A posted reading cannot carry negative consumption.");
        }
    }

    /** An AMI or stream reading: interval consumption, posted at once - it arrived authenticated. */
    public static ConsumptionReading interval(UUID id, EnergyMeter meter, Instant intervalStart, Instant observedAt,
            BigDecimal consumption, BigDecimal vendorValue, String vendorUnit, String sourceReference, String actorId,
            Instant at, SourceChannel channel, String correlationId) {
        if (consumption == null || consumption.signum() < 0) {
            throw new FacilitiesException.ValidationFailedException(
                    "Interval consumption must be zero or more; a negative interval is a vendor defect.");
        }
        return new ConsumptionReading(id, meter.siteCode(), meter.id(), meter.buildingCode(), meter.utility(),
                meter.source(), ReadingStatus.POSTED, observedAt, intervalStart, null, consumption, vendorValue,
                vendorUnit, sourceReference, false, null, null, null, null, null, null, null, null, null, null,
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * A manual register read, posted or held according to the plausibility band.
     *
     * @param consumption the delta from the previous posted register read, or {@code null} for a baseline
     */
    public static ConsumptionReading manual(UUID id, EnergyMeter meter, Instant observedAt, Instant intervalStart,
            BigDecimal registerValue, BigDecimal consumption, PlausibilityPolicy.Outcome plausibility, String note,
            String actorId, Instant at, SourceChannel channel, String correlationId) {
        boolean plausible = plausibility.plausible();
        return new ConsumptionReading(id, meter.siteCode(), meter.id(), meter.buildingCode(), meter.utility(),
                MeterSource.MANUAL, plausible ? ReadingStatus.POSTED : ReadingStatus.HELD, observedAt,
                intervalStart, registerValue, consumption, null, null, null,
                plausibility.evaluated(), plausibility.trailingDailyAverage(), plausibility.bandLow(),
                plausibility.bandHigh(), plausible ? null : plausibility.reason(), actorId, at, null, null, null,
                note == null || note.isBlank() ? null : note.strip(),
                RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }

    /**
     * A supervisor accepts a held reading into the consumption record.
     *
     * @param consumption the consumption to post - recomputed against the register as it stands now, or
     *        supplied by the verifier when the register went backwards (a replaced or rolled-over meter)
     */
    public ConsumptionReading verify(BigDecimal consumption, String verifierId, String verificationNote, Instant at,
            SourceChannel channel, String correlationId) {
        requireHeldAndAnotherPerson(ReadingStatus.POSTED, verifierId);
        if (consumption == null || consumption.signum() < 0) {
            throw new FacilitiesException.ValidationFailedException(
                    "The register is lower than the previous posted read. Supply the consumption to post "
                            + "(for a replaced or rolled-over meter) or reject the reading.");
        }
        return withDecision(ReadingStatus.POSTED, consumption, verifierId, verificationNote, at, channel,
                correlationId);
    }

    /** A supervisor refuses a held reading. It stays on file, out of the consumption record. */
    public ConsumptionReading reject(String verifierId, String verificationNote, Instant at, SourceChannel channel,
            String correlationId) {
        requireHeldAndAnotherPerson(ReadingStatus.REJECTED, verifierId);
        if (verificationNote == null || verificationNote.isBlank()) {
            throw new FacilitiesException.ValidationFailedException("Rejecting a held reading requires a reason.");
        }
        return withDecision(ReadingStatus.REJECTED, consumption, verifierId, verificationNote, at, channel,
                correlationId);
    }

    public boolean isPosted() {
        return status == ReadingStatus.POSTED;
    }

    private void requireHeldAndAnotherPerson(ReadingStatus target, String verifierId) {
        if (!status.canTransitionTo(target)) {
            throw new FacilitiesException.InvalidStateTransitionException(
                    "Only a held reading can be verified or rejected; this one is " + status + ".");
        }
        // "Held for supervisor verification" (S157-01) means somebody else. The permission matrix keeps
        // the energy officer from verifying at all; this keeps a director who entered a read from
        // verifying their own, which no role boundary could.
        if (enteredBy != null && enteredBy.equals(verifierId)) {
            throw new FacilitiesException(FacilitiesErrorCode.ENERGY_SELF_VERIFICATION);
        }
    }

    private ConsumptionReading withDecision(ReadingStatus target, BigDecimal consumption, String verifierId,
            String verificationNote, Instant at, SourceChannel channel, String correlationId) {
        return new ConsumptionReading(id, siteCode, meterId, buildingCode, utility, source, target, observedAt,
                intervalStart, registerValue, consumption, vendorValue, vendorUnit, sourceReference,
                plausibilityChecked, trailingDailyAverage, bandLow, bandHigh, holdReason, enteredBy, enteredAt,
                verifierId, at, verificationNote == null || verificationNote.isBlank() ? null : verificationNote.strip(),
                note, metadata.modifiedBy(verifierId, at, channel, correlationId));
    }
}
