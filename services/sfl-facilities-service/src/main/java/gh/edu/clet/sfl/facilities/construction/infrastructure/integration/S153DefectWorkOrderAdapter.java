package gh.edu.clet.sfl.facilities.construction.infrastructure.integration;

import gh.edu.clet.sfl.facilities.construction.application.ports.DefectWorkOrderPort;
import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import gh.edu.clet.sfl.facilities.maintenance.application.AutomatedWorkOrderIntake;
import gh.edu.clet.sfl.facilities.maintenance.domain.FaultPriority;
import gh.edu.clet.sfl.facilities.maintenance.domain.WorkOrderStatus;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Defects-liability items as S153 work orders - SRS-SFL-S176-04 "raise S153 work orders tagged to the
 * project and contractor rather than as ordinary maintenance". The one class in S176 that names S153.
 *
 * <h2>How the tag is carried</h2>
 *
 * Category {@value #CATEGORY}, which is what an S153 reader filters on to tell a liability job from
 * ordinary maintenance; origin module {@code S176}; and an origin reference naming the project, the
 * defect and the liable contractor. The contractor is not an S153 vendor - S176 contractors and S153
 * maintenance vendors are separate registers until the Vendor Master (S133) joins them - so it is
 * carried in the reference rather than as the work order's {@code vendorId}.
 *
 * <p>Idempotent on {@code construction-defect:<defectId>}, so a retried raise returns the same order.
 */
@Component
public class S153DefectWorkOrderAdapter implements DefectWorkOrderPort {

    static final String CATEGORY = "CONSTRUCTION_DEFECT";

    private final AutomatedWorkOrderIntake intake;

    public S153DefectWorkOrderAdapter(AutomatedWorkOrderIntake intake) {
        this.intake = intake;
    }

    @Override
    public RaisedDefectWorkOrder raise(DefectItem defect, ConstructionProject project, Contractor contractor,
            String requestedBy, String correlationId) {
        AutomatedWorkOrderIntake.RaisedWorkOrder order = intake.raise(new AutomatedWorkOrderIntake.AutomatedWorkOrderRequest(
                defect.siteCode(), defect.roomId(), defect.locationCode(), null,
                "Defects liability " + defect.defectReference() + " - " + project.projectReference(),
                defect.description(), CATEGORY, FaultPriority.valueOf(defect.priority().name()), "S176",
                originReference(project, defect, contractor), null, requestedBy, null, null, SourceChannel.SYSTEM,
                correlationId, "construction-defect:" + defect.id()));
        return view(order);
    }

    @Override
    public Optional<RaisedDefectWorkOrder> find(UUID workOrderId) {
        return intake.find(workOrderId).map(S153DefectWorkOrderAdapter::view);
    }

    static String originReference(ConstructionProject project, DefectItem defect, Contractor contractor) {
        return "project:" + project.projectReference() + "/defect:" + defect.defectReference() + "/contractor:"
                + contractor.contractorCode();
    }

    private static RaisedDefectWorkOrder view(AutomatedWorkOrderIntake.RaisedWorkOrder order) {
        return new RaisedDefectWorkOrder(order.workOrderId(), order.workOrderNumber(), order.faultNumber(),
                order.status().name(), order.status() == WorkOrderStatus.CLOSED);
    }
}
