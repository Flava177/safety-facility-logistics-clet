package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence.notification;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationIntentRepository extends JpaRepository<NotificationIntentEntity, UUID> {
}
