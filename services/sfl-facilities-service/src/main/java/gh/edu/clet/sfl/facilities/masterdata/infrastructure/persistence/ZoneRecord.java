package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "zones", schema = "facilities")
public class ZoneRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "zone_code", nullable = false, length = 60)
    private String zoneCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 300)
    private String purpose;
    @Column(name = "parent_zone_id")
    private UUID parentZoneId;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected ZoneRecord() {
    }

    private ZoneRecord(Zone zone) {
        id = zone.id();
        siteCode = zone.siteCode();
        zoneCode = zone.zoneCode();
        name = zone.name();
        purpose = zone.purpose();
        parentZoneId = zone.parentZoneId();
        lifecycleStatus = zone.lifecycleStatus();
        metadata = RecordMetadataEmbeddable.from(zone.metadata());
    }

    public static ZoneRecord from(Zone zone) {
        return new ZoneRecord(zone);
    }

    public Zone toDomain() {
        return new Zone(id, siteCode, zoneCode, name, purpose, parentZoneId, lifecycleStatus,
                metadata.toDomain());
    }
}
