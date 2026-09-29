package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaDefectItemJpaRepository extends JpaRepository<DefectItemRecord, UUID> {

    List<DefectItemRecord> findByProjectId(UUID projectId);

    List<DefectItemRecord> findByStatusOrderByRaisedAtAsc(DefectItem.Status status, Pageable pageable);

    /** See {@code JpaFacilityFaultRepository.nextFaultSequence} for why this is a sequence. */
    @Query(value = "select nextval('facilities.construction_defect_reference_seq')", nativeQuery = true)
    long nextDefectSequence();
}
