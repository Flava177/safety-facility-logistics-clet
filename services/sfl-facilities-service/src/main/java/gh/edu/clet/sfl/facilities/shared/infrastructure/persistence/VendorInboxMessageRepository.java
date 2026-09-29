package gh.edu.clet.sfl.facilities.shared.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface VendorInboxMessageRepository extends JpaRepository<VendorInboxMessageEntity, UUID> {

    @Query("select count(m) > 0 from VendorInboxMessageEntity m where m.sourceSystem = :source "
            + "and m.idempotencyKey = :key and m.outcome = 'ACCEPTED'")
    boolean existsAccepted(@Param("source") String source, @Param("key") String key);
}
