package gh.edu.clet.sfl.fleetlogistics.fleet.infrastructure.persistence;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowStatus;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.FleetWorkflowType;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.OperatingMode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.WorkflowPriority;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Append-only workflow comments.
 *
 * <p>Top-level by necessity: Spring Data only registers repository interfaces that are top-level
 * types, so a nested interface is silently never created as a bean.
 */
interface WorkflowCommentJpaRepository extends JpaRepository<WorkflowCommentEntity, UUID> {

    List<WorkflowCommentEntity> findByWorkflowItemIdOrderByOccurredAtAsc(UUID workflowItemId);
}
