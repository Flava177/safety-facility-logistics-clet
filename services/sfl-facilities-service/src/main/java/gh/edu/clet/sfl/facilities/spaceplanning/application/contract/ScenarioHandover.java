package gh.edu.clet.sfl.facilities.spaceplanning.application.contract;

import java.util.Optional;
import java.util.UUID;

/**
 * How S176 confirms, at handover, the S158 scenario its works realised - SRS-SFL-S176-04.
 *
 * <p>"Handover updates S152 and, where the project committed an S158 scenario, confirms that commit."
 * S158 owns the scenario and decides what confirming it means; S176 only says the works are done.
 */
public interface ScenarioHandover {

    Optional<ScenarioSummary> find(UUID scenarioId);

    /** Idempotent: confirming an already-confirmed scenario for the same project returns it unchanged. */
    ScenarioSummary confirmHandover(UUID scenarioId, UUID projectId, String projectReference, String confirmedBy);

    enum Status {
        DRAFT,
        COMMITTED,
        HANDED_OVER,
        DISCARDED
    }

    record ScenarioSummary(UUID scenarioId, String siteCode, String name, Status status, UUID linkedProjectId) {
    }
}
