package gh.edu.clet.sfl.facilities.construction.application.ports;

import java.util.UUID;

/**
 * S158 Space Planning, as handover confirms a committed scenario - SRS-SFL-S176-04 "where the project
 * committed an S158 scenario, confirms that commit".
 *
 * <p>Implemented over the {@code spaceplanning.application.contract.ScenarioHandover} interface only;
 * S158 is being built in parallel and S176 depends on nothing of it but that contract.
 */
public interface ScenarioConfirmationPort {

    Result confirm(UUID scenarioId, UUID projectId, String projectReference, String confirmedBy);

    /**
     * @param confirmed S158 accepted the confirmation
     * @param detail why not, when it did not - "S158 has no record of scenario ..." until S158 ships
     */
    record Result(boolean confirmed, String detail) {
    }
}
