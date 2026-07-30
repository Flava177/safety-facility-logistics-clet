package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import gh.edu.clet.sfl.facilities.shared.domain.model.OperatingMode;
import gh.edu.clet.sfl.facilities.shared.domain.model.RecordLifecycleStatus;
import gh.edu.clet.sfl.facilities.shared.infrastructure.persistence.RecordMetadataEmbeddable;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sites", schema = "facilities")
public class SiteRecord {

    @Id
    private UUID id;
    @Column(name = "site_code", nullable = false, length = 40, unique = true)
    private String siteCode;
    @Column(nullable = false, length = 160)
    private String name;
    @Column(length = 1000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private RecordLifecycleStatus lifecycleStatus;
    @Enumerated(EnumType.STRING)
    @Column(name = "operating_mode", nullable = false, length = 20)
    private OperatingMode operatingMode;
    @Column(name = "operating_mode_changed_at")
    private Instant operatingModeChangedAt;
    @Column(name = "operating_mode_changed_by", length = 160)
    private String operatingModeChangedBy;
    @Embedded
    private RecordMetadataEmbeddable metadata;

    protected SiteRecord() {
    }

    private SiteRecord(Site site) {
        id = site.id();
        siteCode = site.siteCode();
        name = site.name();
        description = site.description();
        lifecycleStatus = site.lifecycleStatus();
        operatingMode = site.operatingMode();
        operatingModeChangedAt = site.operatingModeChangedAt();
        operatingModeChangedBy = site.operatingModeChangedBy();
        metadata = RecordMetadataEmbeddable.from(site.metadata());
    }

    public static SiteRecord from(Site site) {
        return new SiteRecord(site);
    }

    public Site toDomain() {
        return new Site(id, siteCode, name, description, lifecycleStatus, operatingMode,
                operatingModeChangedAt, operatingModeChangedBy, metadata.toDomain());
    }
}
