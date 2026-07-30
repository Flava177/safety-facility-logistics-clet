package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMembership;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "facility_zone_memberships", schema = "facilities")
public class ZoneMembershipRecord {

    @Id
    private UUID id;
    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;
    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 20)
    private ZoneMemberType memberType;
    @Column(name = "member_id", nullable = false)
    private UUID memberId;
    @Column(name = "site_code", nullable = false, length = 40)
    private String siteCode;
    @Column(name = "added_by", nullable = false, length = 160)
    private String addedBy;
    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected ZoneMembershipRecord() {
    }

    private ZoneMembershipRecord(ZoneMembership membership) {
        id = membership.id();
        zoneId = membership.zoneId();
        memberType = membership.memberType();
        memberId = membership.memberId();
        siteCode = membership.siteCode();
        addedBy = membership.addedBy();
        addedAt = membership.addedAt();
    }

    public static ZoneMembershipRecord from(ZoneMembership membership) {
        return new ZoneMembershipRecord(membership);
    }

    public ZoneMembership toDomain() {
        return new ZoneMembership(id, zoneId, memberType, memberId, siteCode, addedBy, addedAt);
    }
}
