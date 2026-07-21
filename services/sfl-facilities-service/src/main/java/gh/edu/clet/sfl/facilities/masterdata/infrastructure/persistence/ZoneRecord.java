package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.masterdata.domain.Zone;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ZoneRecord() {
    }

    private ZoneRecord(Zone zone) {
        id = zone.id();
        siteCode = zone.siteCode();
        zoneCode = zone.zoneCode();
        name = zone.name();
        purpose = zone.purpose();
        createdAt = zone.createdAt();
    }

    public static ZoneRecord from(Zone zone) {
        return new ZoneRecord(zone);
    }

    public Zone toDomain() {
        return new Zone(id, siteCode, zoneCode, name, purpose, createdAt);
    }
}
