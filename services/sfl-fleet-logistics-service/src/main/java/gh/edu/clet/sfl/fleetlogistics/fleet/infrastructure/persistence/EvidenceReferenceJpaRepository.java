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

    @Query("""
            select evidence from EvidenceReferenceEntity evidence
             where evidence.siteCode = :siteCode
               and lower(evidence.sha256Hash) = lower(:sha256Hash)
               and (:excludingId is null or evidence.id <> :excludingId)
             order by evidence.createdAt asc
            """)
    List<EvidenceReferenceEntity> findBySha256(
            @Param("siteCode") String siteCode,
            @Param("sha256Hash") String sha256Hash,
            @Param("excludingId") UUID excludingId);
}
