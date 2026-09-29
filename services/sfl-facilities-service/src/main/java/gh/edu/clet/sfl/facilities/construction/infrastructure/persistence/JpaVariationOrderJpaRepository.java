package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JpaVariationOrderJpaRepository extends JpaRepository<VariationOrderRecord, UUID> {

    List<VariationOrderRecord> findByProjectId(UUID projectId);

    /** See {@code JpaFacilityFaultRepository.nextFaultSequence} for why this is a sequence. */
    @Query(value = "select nextval('facilities.construction_variation_reference_seq')", nativeQuery = true)
    long nextVariationSequence();
}
