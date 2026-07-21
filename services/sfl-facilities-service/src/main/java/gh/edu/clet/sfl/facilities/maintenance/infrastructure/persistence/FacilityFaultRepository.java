package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FacilityFaultRepository extends JpaRepository<FacilityFaultRecord, UUID> {
}

