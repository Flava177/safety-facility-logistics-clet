package gh.edu.clet.sfl.platform.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventRepository extends JpaRepository<AuditEventRecord, UUID> {
}

