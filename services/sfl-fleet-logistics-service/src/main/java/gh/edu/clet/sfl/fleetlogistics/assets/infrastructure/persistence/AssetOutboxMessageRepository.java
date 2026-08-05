package gh.edu.clet.sfl.fleetlogistics.assets.infrastructure.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AssetOutboxMessageRepository extends JpaRepository<AssetOutboxMessageRecord, UUID> {
}