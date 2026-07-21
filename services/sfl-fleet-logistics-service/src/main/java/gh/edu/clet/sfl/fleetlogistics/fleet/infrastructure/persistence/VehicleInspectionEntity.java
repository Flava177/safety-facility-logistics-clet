package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.DefectSeverity;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionFinding;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionResult;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.InspectionType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.VehicleInspection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Persistence image of {@link VehicleInspection}.
 *
 * <p>Findings are stored as JSONB because a checklist genuinely varies by vehicle category and by the
 * local checklist in force; the rest of the record is columnar so it can be indexed and constrained.
 */
@Entity
@Table(name = "vehicle_inspections", schema = "fleet_logistics")
public class VehicleInspectionEntity {

    private static final TypeReference<List<FindingRow>> FINDING_LIST = new TypeReference<>() {
    };

    @Id
    private UUID id;

    @Column(name = "vehicle_id", nullable = false)
    private UUID vehicleId;

    @Column(name = "trip_id")
    private UUID tripId;

    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "inspection_type", nullable = false, length = 40)
    private InspectionType inspectionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InspectionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private InspectionResult result;

    @Column(name = "performed_by", nullable = false, length = 160)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;

    @Column(name = "odometer_reading", nullable = false)
    private long odometerReading;

    @Column(name = "evidence_id")
    private UUID evidenceId;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String findings;

    @Column(length = 2000)
    private String notes;

    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;

    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 40)
    private SourceChannel sourceChannel;

    @Column(name = "audit_correlation_id", length = 120)
    private String auditCorrelationId;

    protected VehicleInspectionEntity() {
    }

    public static VehicleInspectionEntity from(VehicleInspection inspection, ObjectMapper objectMapper) {
        VehicleInspectionEntity entity = new VehicleInspectionEntity();
        entity.id = inspection.id();
        entity.applyFrom(inspection, objectMapper);
        return entity;
    }

    public void applyFrom(VehicleInspection inspection, ObjectMapper objectMapper) {
        this.vehicleId = inspection.vehicleId();
        this.tripId = inspection.tripId();
        this.siteCode = inspection.siteCode().value();
        this.inspectionType = inspection.inspectionType();
        this.status = inspection.status();
        this.result = inspection.result();
        this.performedBy = inspection.performedBy();
        this.performedAt = inspection.performedAt();
        this.odometerReading = inspection.odometerReading();
        this.evidenceId = inspection.evidenceId();
        this.findings = writeFindings(inspection.findings(), objectMapper);
        this.notes = inspection.notes();
        this.createdBy = inspection.metadata().createdBy();
        this.createdAt = inspection.metadata().createdAt();
        this.lastModifiedBy = inspection.metadata().lastModifiedBy();
        this.lastModifiedAt = inspection.metadata().lastModifiedAt();
        this.sourceChannel = inspection.metadata().sourceChannel();
        this.auditCorrelationId = inspection.metadata().auditCorrelationId();
    }

    public VehicleInspection toDomain(ObjectMapper objectMapper) {
        return new VehicleInspection(id, vehicleId, tripId, SiteCode.of(siteCode), inspectionType, status, result,
                performedBy, performedAt, odometerReading, evidenceId, readFindings(objectMapper), notes,
                RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt, version,
                        sourceChannel, auditCorrelationId));
    }

    private static String writeFindings(List<InspectionFinding> findings, ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsString(findings.stream()
                    .map(finding -> new FindingRow(finding.checkCode(), finding.description(),
                            finding.severity().name(), finding.resolved(), finding.resolutionReference()))
                    .toList());
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialise the inspection findings", exception);
        }
    }

    private List<InspectionFinding> readFindings(ObjectMapper objectMapper) {
        if (findings == null || findings.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(findings, FINDING_LIST).stream()
                    .map(row -> new InspectionFinding(row.checkCode(), row.description(),
                            DefectSeverity.valueOf(row.severity()), row.resolved(), row.resolutionReference()))
                    .toList();
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not read the stored inspection findings", exception);
        }
    }

    /** Storage shape of a finding; kept separate so the domain type stays free of serialisation concerns. */
    record FindingRow(String checkCode, String description, String severity, boolean resolved,
            String resolutionReference) {
    }
}
