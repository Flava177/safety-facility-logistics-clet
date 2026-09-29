package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaOccupancyStandardJpaRepository extends JpaRepository<OccupancyStandardRecord, UUID> {

    @Query("""
            select s from OccupancyStandardRecord s
            where s.siteCode = :siteCode and s.spaceType = :spaceType and s.supersededAt is null
            """)
    Optional<OccupancyStandardRecord> findActive(@Param("siteCode") String siteCode,
            @Param("spaceType") SpaceType spaceType);

    List<OccupancyStandardRecord> findBySiteCodeOrderBySpaceTypeAscVersionNumberDesc(String siteCode);

    @Query("""
            select coalesce(max(s.versionNumber), 0) from OccupancyStandardRecord s
            where s.siteCode = :siteCode and s.spaceType = :spaceType
            """)
    int maxVersion(@Param("siteCode") String siteCode, @Param("spaceType") SpaceType spaceType);
}
