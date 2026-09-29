package gh.edu.clet.sfl.facilities.cleaning.domain;

import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import gh.edu.clet.sfl.facilities.shared.domain.model.EstateCodes;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordMetadata;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A computed SLA breach on a vendor's scorecard - SRS-SFL-S169-03.
 *
 * <p>{@code basis} states the timestamps or rating it was computed from, in words ("started 95 min
 * after the request was raised; contracted 60 min"), so a vendor disputing it can be shown exactly
 * what the system measured - and that nothing it reported was used.
 *
 * @param contractedValue minutes for RESPONSE and COMPLETION; the rating floor for QUALITY
 * @param actualValue minutes measured from the task's own timestamps; the occupant rating for QUALITY
 */
public record SlaBreach(
        UUID id,
        String siteCode,
        UUID vendorId,
        UUID taskId,
        UUID slaTermsId,
        SlaBreachType type,
        BigDecimal contractedValue,
        BigDecimal actualValue,
        String basis,
        Instant recordedAt,
        RecordMetadata metadata) {

    public SlaBreach {
        Objects.requireNonNull(id, "id is required");
        siteCode = EstateCodes.normalize(siteCode);
        Objects.requireNonNull(vendorId, "vendorId is required");
        Objects.requireNonNull(taskId, "taskId is required");
        Objects.requireNonNull(slaTermsId, "slaTermsId is required");
        Objects.requireNonNull(type, "type is required");
        Objects.requireNonNull(contractedValue, "contractedValue is required");
        Objects.requireNonNull(actualValue, "actualValue is required");
        EstateCodes.require(basis, "basis");
        Objects.requireNonNull(recordedAt, "recordedAt is required");
        Objects.requireNonNull(metadata, "metadata is required");
    }

    public static SlaBreach record(UUID id, CleaningTask task, VendorSlaTerms terms, SlaBreachType type,
            BigDecimal contracted, BigDecimal actual, String basis, String actorId, Instant at, SourceChannel channel,
            String correlationId) {
        return new SlaBreach(id, task.siteCode(), task.vendorId(), task.id(), terms.id(), type, contracted, actual,
                basis, at, RecordMetadata.createdBy(actorId, at, channel, correlationId));
    }
}
