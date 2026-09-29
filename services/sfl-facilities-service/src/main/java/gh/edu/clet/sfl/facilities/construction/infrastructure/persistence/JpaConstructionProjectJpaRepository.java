package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.ProjectStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaConstructionProjectJpaRepository extends JpaRepository<ConstructionProjectRecord, UUID> {

    Optional<ConstructionProjectRecord> findBySpaceChangeRequestId(UUID spaceChangeRequestId);

    @Query("""
            select p from ConstructionProjectRecord p
            where (:siteCode is null or p.siteCode = :siteCode)
              and (:status is null or p.status = :status)
            order by p.registeredAt desc
            """)
    Page<ConstructionProjectRecord> search(@Param("siteCode") String siteCode, @Param("status") ProjectStatus status,
            Pageable pageable);

    @Query("select p from ConstructionProjectRecord p where (:siteCode is null or p.siteCode = :siteCode)")
    List<ConstructionProjectRecord> findForSite(@Param("siteCode") String siteCode);

    List<ConstructionProjectRecord> findByStatus(ProjectStatus status);

    /** See {@code JpaFacilityFaultRepository.nextFaultSequence} for why this is a sequence. */
    @Query(value = "select nextval('facilities.construction_project_reference_seq')", nativeQuery = true)
    long nextProjectSequence();
}
