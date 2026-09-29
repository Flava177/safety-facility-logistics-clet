package gh.edu.clet.sfl.facilities.construction.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JpaPermitProjectionJpaRepository extends JpaRepository<PermitProjectionRecord, UUID> {

    Optional<PermitProjectionRecord> findByPermitId(String permitId);

    List<PermitProjectionRecord> findByPermitIdIn(Collection<String> permitIds);

    List<PermitProjectionRecord> findByContractorReferenceIn(Collection<String> contractorReferences);
}
