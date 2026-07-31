package gh.edu.clet.sfl.facilities.masterdata.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.ZoneMemberType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface JpaZoneMembershipRepository extends JpaRepository<ZoneMembershipRecord, UUID> {

    List<ZoneMembershipRecord> findByZoneIdOrderByMemberTypeAscAddedAtAsc(UUID zoneId);

    /** "Which zones is this room in" — what S162a and S174 will ask. */
    List<ZoneMembershipRecord> findByMemberTypeAndMemberId(ZoneMemberType memberType, UUID memberId);

    Optional<ZoneMembershipRecord> findByZoneIdAndMemberTypeAndMemberId(UUID zoneId, ZoneMemberType memberType,
            UUID memberId);

    void deleteByZoneIdAndMemberTypeAndMemberId(UUID zoneId, ZoneMemberType memberType, UUID memberId);
}
