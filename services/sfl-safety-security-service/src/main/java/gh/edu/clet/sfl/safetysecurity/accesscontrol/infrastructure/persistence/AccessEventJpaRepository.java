package gh.edu.clet.sfl.safetysecurity.accesscontrol.infrastructure.persistence;

import gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.model.AccessEventKind;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AccessEventJpaRepository extends JpaRepository<AccessEventJpaEntity, UUID> {

    Optional<AccessEventJpaEntity> findBySourceAndExternalEventId(String source, String externalEventId);

    @Query("""
            SELECT e FROM AccessEventJpaEntity e
            WHERE e.siteCode = :siteCode AND e.zoneCode = :zoneCode
              AND (:personRef IS NULL OR e.personRef = :personRef)
            ORDER BY e.occurredAt DESC
            """)
    List<AccessEventJpaEntity> findRecent(@Param("siteCode") String siteCode, @Param("zoneCode") String zoneCode,
            @Param("personRef") String personRef, Pageable pageable);

    @Query("""
            SELECT e FROM AccessEventJpaEntity e
            WHERE e.siteCode = :siteCode AND e.zoneCode = :zoneCode AND e.personRef = :personRef
              AND e.kind = :kind
            ORDER BY e.occurredAt DESC
            """)
    List<AccessEventJpaEntity> findGrantedForPerson(@Param("siteCode") String siteCode,
            @Param("zoneCode") String zoneCode, @Param("personRef") String personRef,
            @Param("kind") AccessEventKind kind, Pageable pageable);
}
