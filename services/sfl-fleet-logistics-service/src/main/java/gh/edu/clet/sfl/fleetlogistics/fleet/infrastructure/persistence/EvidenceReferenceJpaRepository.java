package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EvidenceReferenceJpaRepository extends JpaRepository<EvidenceReferenceEntity, UUID> {

    @Query("""
            select evidence from EvidenceReferenceEntity evidence
             where (:allSites = true or evidence.siteCode in :siteScopes)
               and evidence.relatedRecordType = :relatedRecordType
               and evidence.relatedRecordId = :relatedRecordId
             order by evidence.createdAt desc, evidence.id desc
            """)
    List<EvidenceReferenceEntity> findByRelatedRecordInScope(
            @Param("allSites") boolean allSites,
            @Param("siteScopes") List<String> siteScopes,
            @Param("relatedRecordType") String relatedRecordType,
            @Param("relatedRecordId") String relatedRecordId);
}
