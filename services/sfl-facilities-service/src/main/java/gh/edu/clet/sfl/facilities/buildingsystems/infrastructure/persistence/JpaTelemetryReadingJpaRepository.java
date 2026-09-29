package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface JpaTelemetryReadingJpaRepository extends JpaRepository<TelemetryReadingRecord, UUID> {

    List<TelemetryReadingRecord> findBySourceIdAndIdempotencyKeyOrderByItemIndex(String sourceId,
            String idempotencyKey);

    @Query("select r from TelemetryReadingRecord r where r.siteCode = :siteCode "
            + "and (:deviceId is null or r.deviceId = :deviceId) and (:channel is null or r.channel = :channel) "
            + "and (:from is null or r.observedAt >= :from) and (:to is null or r.observedAt < :to) "
            + "order by r.observedAt desc")
    List<TelemetryReadingRecord> search(@Param("siteCode") String siteCode, @Param("deviceId") UUID deviceId,
            @Param("channel") String channel, @Param("from") Instant from, @Param("to") Instant to,
            org.springframework.data.domain.Pageable page);

    @Modifying
    @Query("update TelemetryReadingRecord r set r.evidenceHold = true where r.id in :ids")
    void holdAsEvidence(@Param("ids") List<UUID> ids);

    @Modifying
    @Query("delete from TelemetryReadingRecord r where r.siteCode = :siteCode and r.observedAt < :cutoff "
            + "and r.evidenceHold = false")
    int purge(@Param("siteCode") String siteCode, @Param("cutoff") Instant cutoff);
}
