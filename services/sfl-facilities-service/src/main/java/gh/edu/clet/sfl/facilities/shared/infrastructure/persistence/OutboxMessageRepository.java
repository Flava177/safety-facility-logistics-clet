package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxMessageRepository extends JpaRepository<OutboxMessageRecord, UUID> {
}