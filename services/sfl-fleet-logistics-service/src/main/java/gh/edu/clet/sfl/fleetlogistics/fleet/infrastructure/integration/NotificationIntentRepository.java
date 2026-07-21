package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.integration;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationIntentRepository extends JpaRepository<NotificationIntentEntity, UUID> {

    List<NotificationIntentEntity> findBySubjectReferenceOrderByCreatedAtDesc(String subjectReference);

    long countByStatus(String status);
}
