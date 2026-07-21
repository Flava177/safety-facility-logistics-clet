package gh.edu.clet.sfl.assetvisibility.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface OutboxMessageRepository extends JpaRepository<OutboxMessageRecord, UUID> {
}