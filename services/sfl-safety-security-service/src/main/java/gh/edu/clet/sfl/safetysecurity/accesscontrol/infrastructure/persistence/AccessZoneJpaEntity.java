package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessZone;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.RecordMetadata;
import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.SourceChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JPA mapping for {@link AccessZone}. {@code doorGroups} and {@code lockedDoorGroups} are stored as
 * plain delimited text, not JSON - the same "a handful of codes does not earn a second table or a
 * structured format" reasoning {@code VisitorVisitJpaEntity} gives for {@code accessZones}.
 * {@code doorGroupsText} format: {@code group=door1|door2;group2=door3} (empty string if none).
 */
@Entity
@Table(name = "access_zones", schema = "safety_security")
public class AccessZoneJpaEntity {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 80)
    private String siteCode;
    @Column(name = "zone_code", nullable = false, length = 80)
    private String zoneCode;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(name = "location_ref", length = 120)
    private String locationRef;
    @Column(nullable = false, length = 500)
    private String schedule;
    @Column(name = "door_groups_text", length = 2000)
    private String doorGroupsText;
    @Column(name = "examination_mode", nullable = false)
    private boolean examinationMode;
    @Column(name = "locked_door_groups_text", length = 500)
    private String lockedDoorGroupsText;
    @Column(name = "examination_until")
    private Instant examinationUntil;
    @Column(name = "created_by", nullable = false, length = 160)
    private String createdBy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "last_modified_by", nullable = false, length = 160)
    private String lastModifiedBy;
    @Column(name = "last_modified_at", nullable = false)
    private Instant lastModifiedAt;
    @Version
    @Column(name = "record_version", nullable = false)
    private long recordVersion;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_channel", nullable = false, length = 20)
    private SourceChannel sourceChannel;
    @Column(name = "correlation_id", length = 120)
    private String correlationId;

    protected AccessZoneJpaEntity() {
    }

    public void apply(AccessZone zone) {
        id = zone.id();
        siteCode = zone.siteCode();
        zoneCode = zone.zoneCode();
        name = zone.name();
        locationRef = zone.locationRef();
        schedule = zone.schedule();
        doorGroupsText = encodeDoorGroups(zone.doorGroups());
        examinationMode = zone.examinationMode();
        lockedDoorGroupsText = String.join(",", zone.lockedDoorGroups());
        examinationUntil = zone.examinationUntil();
        RecordMetadata metadata = zone.metadata();
        createdBy = metadata.createdBy();
        createdAt = metadata.createdAt();
        lastModifiedBy = metadata.lastModifiedBy();
        lastModifiedAt = metadata.lastModifiedAt();
        sourceChannel = metadata.sourceChannel();
        correlationId = metadata.correlationId();
    }

    public AccessZone toDomain() {
        RecordMetadata metadata = RecordMetadata.rehydrate(createdBy, createdAt, lastModifiedBy, lastModifiedAt,
                recordVersion, sourceChannel, correlationId);
        List<String> lockedGroups = lockedDoorGroupsText == null || lockedDoorGroupsText.isBlank() ? List.of()
                : Arrays.asList(lockedDoorGroupsText.split(","));
        return new AccessZone(id, siteCode, zoneCode, name, locationRef, schedule, decodeDoorGroups(doorGroupsText),
                examinationMode, lockedGroups, examinationUntil, metadata);
    }

    public UUID getId() {
        return id;
    }

    static String encodeDoorGroups(Map<String, List<String>> doorGroups) {
        if (doorGroups == null || doorGroups.isEmpty()) {
            return "";
        }
        return doorGroups.entrySet().stream()
                .map(e -> e.getKey() + "=" + String.join("|", e.getValue()))
                .collect(Collectors.joining(";"));
    }

    static Map<String, List<String>> decodeDoorGroups(String text) {
        if (text == null || text.isBlank()) {
            return Map.of();
        }
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String group : text.split(";")) {
            int eq = group.indexOf('=');
            if (eq < 0) {
                continue;
            }
            String name = group.substring(0, eq);
            String doors = group.substring(eq + 1);
            result.put(name, doors.isBlank() ? List.of() : Arrays.asList(doors.split("\\|")));
        }
        return result;
    }
}
