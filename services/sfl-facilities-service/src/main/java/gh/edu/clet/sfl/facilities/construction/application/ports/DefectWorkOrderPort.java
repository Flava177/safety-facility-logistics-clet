package gh.edu.clet.sfl.facilities.construction.application.ports;

import gh.edu.clet.sfl.facilities.construction.domain.ConstructionProject;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.DefectItem;
import java.util.Optional;
import java.util.UUID;

/**
 * S153 as S176 needs it: raise a defects-liability work order, and read back its state -
 * SRS-SFL-S176-04.
 *
 * <p>Implemented over {@code AutomatedWorkOrderIntake} by the one adapter allowed to name S153. A port
 * of S176's own so the defect rules can be tested with a double whose work orders a test closes by
 * hand, and so the category and tagging - {@code CONSTRUCTION_DEFECT}, the project, the contractor -
 * are decided in exactly one place.
 */
public interface DefectWorkOrderPort {

    RaisedDefectWorkOrder raise(DefectItem defect, ConstructionProject project, Contractor contractor,
            String requestedBy, String correlationId);

    Optional<RaisedDefectWorkOrder> find(UUID workOrderId);

    /**
     * @param closed the work order is CLOSED in S153 - the one state that resolves a defect
     */
    record RaisedDefectWorkOrder(UUID workOrderId, String workOrderNumber, String faultNumber, String status,
            boolean closed) {
    }
}
