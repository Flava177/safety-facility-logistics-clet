package gh.edu.clet.sfl.safetysecurity.intrusion.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface IntrusionAlarmJpaRepository extends JpaRepository<IntrusionAlarmJpaEntity, UUID> {

    List<IntrusionAlarmJpaEntity> findBySiteCodeAndStatus(String siteCode, AlarmStatus status);

    @Query("SELECT a FROM IntrusionAlarmJpaEntity a WHERE a.status = 'RAISED'")
    List<IntrusionAlarmJpaEntity> findAllRaised();

    @Query("SELECT a FROM IntrusionAlarmJpaEntity a WHERE a.siteCode = :siteCode AND a.panelId = :panelId "
            + "AND a.zoneCode = :zoneCode AND a.alarmType = :alarmType "
            + "AND a.status IN ('RAISED', 'ACKNOWLEDGED', 'ESCALATED', 'COALESCED')")
    Optional<IntrusionAlarmJpaEntity> findOpen(@Param("siteCode") String siteCode, @Param("panelId") String panelId,
            @Param("zoneCode") String zoneCode, @Param("alarmType") AlarmType alarmType);
}
