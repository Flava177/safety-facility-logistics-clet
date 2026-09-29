package gh.edu.clet.sfl.facilities.buildingsystems.infrastructure.integration;

import gh.edu.clet.sfl.facilities.buildingsystems.application.ports.BuildingWorkOrderPort;
import gh.edu.clet.sfl.facilities.maintenance.application.AutomatedWorkOrderIntake;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * S156's one door into S153 - a real, in-process call to {@link AutomatedWorkOrderIntake}.
 *
 * <p>The only class in {@code buildingsystems} that names a {@code maintenance} type. S153 knows nothing
 * about S156 in return: the category {@code BMS_TELEMETRY}, the origin reference and the evidence reference
 * are values S153 stores and shows, which is what keeps the S156-05 guarantee - "no change is required in
 * S152 or S153 domain code" - true however many vendors S156 grows.
 */
@Component
public class MaintenanceWorkOrderAdapter implements BuildingWorkOrderPort {

    static final String ORIGIN_MODULE = "S156";
    static final String CATEGORY = "BMS_TELEMETRY";

    private final AutomatedWorkOrderIntake intake;

    public MaintenanceWorkOrderAdapter(AutomatedWorkOrderIntake intake) {
        this.intake = intake;
    }

    @Override
    public WorkOrderReference raise(WorkOrderRequest request) {
        AutomatedWorkOrderIntake.RaisedWorkOrder raised = intake.raise(
                new AutomatedWorkOrderIntake.AutomatedWorkOrderRequest(request.siteCode(), request.roomId(),
                        request.locationCode(), null, request.title(), request.description(), CATEGORY,
                        FaultPriority.valueOf(request.priority().name()), ORIGIN_MODULE,
                        "bms-alert:" + request.alertId(), request.evidenceReference(), null, null, null,
                        SourceChannel.INTEGRATION, request.correlationId(), "bms-alert:" + request.alertId()));
        return reference(raised);
    }

    @Override
    public Optional<WorkOrderReference> find(UUID workOrderId) {
        return intake.find(workOrderId).map(MaintenanceWorkOrderAdapter::reference);
    }

    private static WorkOrderReference reference(AutomatedWorkOrderIntake.RaisedWorkOrder raised) {
        return new WorkOrderReference(raised.workOrderId(), raised.workOrderNumber(), raised.faultId(),
                raised.isOpen());
    }
}
