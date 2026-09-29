package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.Handover;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.VersionedRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** JPA mapping for {@link Handover}. Column names match V21 exactly. */
@Entity
@Table(name = "construction_handovers", schema = "facilities")
public class HandoverRecord extends VersionedRecord {

    @Id
    private UUID id;
    @Column(name = "project_id", nullable = false)
    private UUID projectId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Handover.Outcome outcome;
    @Column(name = "incomplete_reason", length = 2000)
    private String incompleteReason;
    @Column(name = "handover_date", nullable = false)
    private LocalDate handoverDate;
    @Column(length = 4000)
    private String notes;
    @Column(name = "register_change_count", nullable = false)
    private int registerChangeCount;
    @Column(name = "scenario_id")
    private UUID scenarioId;
    @Enumerated(EnumType.STRING)
    @Column(name = "scenario_confirmation", nullable = false, length = 30)
    private Handover.ScenarioConfirmation scenarioConfirmation;
    @Column(name = "scenario_confirmation_detail", length = 1000)
    private String scenarioConfirmationDetail;
    @Column(name = "recorded_by", nullable = false, length = 160)
    private String recordedBy;
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected HandoverRecord() {
    }

    static HandoverRecord empty() {
        return new HandoverRecord();
    }

    void apply(Handover handover) {
        id = handover.id();
        projectId = handover.projectId();
        siteCode = handover.siteCode();
        outcome = handover.outcome();
        incompleteReason = handover.incompleteReason();
        handoverDate = handover.handoverDate();
        notes = handover.notes();
        registerChangeCount = handover.registerChangeCount();
        scenarioId = handover.scenarioId();
        scenarioConfirmation = handover.scenarioConfirmation();
        scenarioConfirmationDetail = handover.scenarioConfirmationDetail();
        recordedBy = handover.recordedBy();
        recordedAt = handover.recordedAt();
        metadata = RecordMetadataEmbeddable.from(handover.metadata());
    }

    Handover toDomain() {
        return new Handover(id, projectId, siteCode, outcome, incompleteReason, handoverDate, notes, registerChangeCount,
                scenarioId, scenarioConfirmation, scenarioConfirmationDetail, recordedBy, recordedAt,
                metadata.toDomain(recordVersion()));
    }
}
