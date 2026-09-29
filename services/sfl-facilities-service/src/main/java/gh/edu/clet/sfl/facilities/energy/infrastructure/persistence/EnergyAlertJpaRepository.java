package gh.edu.clet.sfl.facilities.energy.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.energy.domain.EnergyAlert;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EnergyAlertJpaRepository extends JpaRepository<EnergyAlertRecord, UUID> {

    Optional<EnergyAlertRecord> findByAlertKey(String alertKey);

    @Query("""
            select a from EnergyAlertRecord a
             where (:site is null or a.siteCode = :site)
               and (:type is null or a.type = :type)
               and a.raisedAt >= :since
             order by a.raisedAt desc
            """)
    List<EnergyAlertRecord> search(@Param("site") String site, @Param("type") EnergyAlert.EnergyAlertType type,
            @Param("since") Instant since, Pageable page);
}
