package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import gh.edu.clet.sfl.facilities.masterdata.domain.Site;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SiteRecord() {
    }

    private SiteRecord(Site site) {
        id = site.id();
        siteCode = site.siteCode();
        name = site.name();
        description = site.description();
        active = site.active();
        createdAt = site.createdAt();
    }

    public static SiteRecord from(Site site) {
        return new SiteRecord(site);
    }

    public Site toDomain() {
        return new Site(id, siteCode, name, description, active, createdAt);
    }
}
