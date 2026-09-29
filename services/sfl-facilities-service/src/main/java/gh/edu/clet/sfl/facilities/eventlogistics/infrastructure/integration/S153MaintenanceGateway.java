package gh.edu.clet.sfl.facilities.eventlogistics.infrastructure.integration;

import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.GatewayOutcome;
import gh.edu.clet.sfl.facilities.eventlogistics.application.ports.MaintenanceGatewayPort;
import gh.edu.clet.sfl.facilities.eventlogistics.domain.ResourceRequestStatus;
import gh.edu.clet.sfl.facilities.maintenance.application.AutomatedWorkOrderIntake;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Pre-event maintenance through S153's automated work-order intake - SRS-SFL-S173-02.
 *
 * <p>Category {@code EVENT_PRE_MAINTENANCE}, origin {@code S173}, so an S153 reader can tell event work
 * from ordinary maintenance. MEDIUM priority: the intake's SLA clock then gives the job a response
 * target, and a coordinator who needs it faster raises the priority in S153, where the maintenance
 * supervisor can see the trade-off against the rest of the queue. A raised work order is CONFIRMED -
 * S153 has taken the job on - and becomes FULFILLED when S153 completes or closes it.
 */
@Component
public class S153MaintenanceGateway implements MaintenanceGatewayPort {

    static final String CATEGORY = "EVENT_PRE_MAINTENANCE";

    private final AutomatedWorkOrderIntake intake;

    public S153MaintenanceGateway(AutomatedWorkOrderIntake intake) {
        this.intake = intake;
    }

    @Override
    public GatewayOutcome raise(PreEventMaintenance request) {
        AutomatedWorkOrderIntake.RaisedWorkOrder raised = intake.raise(
                new AutomatedWorkOrderIntake.AutomatedWorkOrderRequest(request.siteCode(), request.roomId(),
                        request.locationCode(), null, request.title(), request.description(), CATEGORY,
                        FaultPriority.MEDIUM, "S173", request.originReference(), null, request.requestedBy(), null,
                        null, SourceChannel.SYSTEM, request.correlationId(), request.idempotencyKey()));
        return map(raised);
    }

    @Override
    public Optional<GatewayOutcome> state(String workOrderId) {
        return intake.find(UUID.fromString(workOrderId)).map(S153MaintenanceGateway::map);
    }

    private static GatewayOutcome map(AutomatedWorkOrderIntake.RaisedWorkOrder raised) {
        String reference = raised.workOrderId().toString();
        return switch (raised.status()) {
            case COMPLETED, CLOSED -> GatewayOutcome.of(ResourceRequestStatus.FULFILLED, reference,
                    raised.workOrderNumber() + " " + raised.status() + " in S153.");
            case CANCELLED -> new GatewayOutcome(ResourceRequestStatus.CONFLICTED, reference, null,
                    raised.workOrderNumber() + " was cancelled in S153.", raised.workOrderNumber() + " CANCELLED");
            default -> GatewayOutcome.of(ResourceRequestStatus.CONFIRMED, reference,
                    raised.workOrderNumber() + " " + raised.status() + " in S153 (fault " + raised.faultNumber() + ").");
        };
    }
}
