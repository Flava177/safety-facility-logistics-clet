package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.buildingsystems.domain.MeasuredQuantity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface JpaChannelStateJpaRepository extends JpaRepository<ChannelStateRecord, UUID> {

    Optional<ChannelStateRecord> findByDeviceIdAndChannel(UUID deviceId, String channel);

    List<ChannelStateRecord> findByDeviceIdIn(Collection<UUID> deviceIds);

    List<ChannelStateRecord> findBySiteCodeAndBuildingCodeAndQuantity(String siteCode, String buildingCode,
            MeasuredQuantity quantity);

    @Query("select c from ChannelStateRecord c where c.breachRuleId is not null and c.alertId is null "
            + "order by c.breachStartedAt asc")
    List<ChannelStateRecord> findPendingBreaches(org.springframework.data.domain.Pageable page);
}
