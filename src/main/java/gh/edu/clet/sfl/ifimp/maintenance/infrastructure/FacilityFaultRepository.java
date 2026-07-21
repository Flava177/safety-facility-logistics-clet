package gh.edu.clet.sfl.ifimp.maintenance.infrastructure;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityFaultRepository extends JpaRepository<FacilityFaultRecord, UUID> {
}

