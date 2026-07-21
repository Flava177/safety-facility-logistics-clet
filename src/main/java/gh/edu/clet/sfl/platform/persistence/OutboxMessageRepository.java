package gh.edu.clet.sfl.platform.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxMessageRepository extends JpaRepository<OutboxMessageRecord, UUID> {
}

