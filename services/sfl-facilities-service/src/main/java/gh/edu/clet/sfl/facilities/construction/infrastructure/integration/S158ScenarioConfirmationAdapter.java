package gh.edu.clet.sfl.facilities.construction.infrastructure.integration;

import gh.edu.clet.sfl.facilities.construction.application.ports.ScenarioConfirmationPort;
import gh.edu.clet.sfl.facilities.spaceplanning.application.contract.ScenarioHandover;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Confirms an S158 committed scenario at handover - SRS-SFL-S176-04. Depends on the
 * {@link ScenarioHandover} contract only; S158's internals are not visible from here.
 *
 * <h2>Why it asks {@code find} first</h2>
 *
 * {@code confirmHandover} runs inside the handover's transaction. If S158 throws from it, Spring marks
 * the shared transaction rollback-only and the whole handover - the S152 register update included -
 * fails at commit. That is right when S158 genuinely refuses a scenario it knows about. It is wrong
 * when S158 simply has no record of the scenario, which until S158 is merged is every scenario (the
 * scaffold's {@code find} answers empty and its {@code confirmHandover} throws).
 *
 * <p>So an unknown scenario, or one not in a state S158 would confirm, is reported unconfirmed with a
 * reason and never passed to {@code confirmHandover}; the handover records it UNRESOLVED and the
 * register update stands. A scenario S158 does know is confirmed, and a failure there is a real
 * failure and rolls back.
 */
@Component
public class S158ScenarioConfirmationAdapter implements ScenarioConfirmationPort {

    private final ScenarioHandover scenarios;

    public S158ScenarioConfirmationAdapter(ScenarioHandover scenarios) {
        this.scenarios = scenarios;
    }

    @Override
    public Result confirm(UUID scenarioId, UUID projectId, String projectReference, String confirmedBy) {
        Optional<ScenarioHandover.ScenarioSummary> scenario = scenarios.find(scenarioId);
        if (scenario.isEmpty()) {
            return new Result(false, "S158 has no record of scenario " + scenarioId + ".");
        }
        ScenarioHandover.Status status = scenario.get().status();
        if (status == ScenarioHandover.Status.HANDED_OVER && projectId.equals(scenario.get().linkedProjectId())) {
            return new Result(true, "Already confirmed.");
        }
        if (status != ScenarioHandover.Status.COMMITTED && status != ScenarioHandover.Status.HANDED_OVER) {
            return new Result(false, "S158 scenario " + scenarioId + " is " + status + ", not COMMITTED.");
        }
        ScenarioHandover.ScenarioSummary confirmed = scenarios.confirmHandover(scenarioId, projectId, projectReference,
                confirmedBy);
        return confirmed.status() == ScenarioHandover.Status.HANDED_OVER
                ? new Result(true, null)
                : new Result(false, "S158 answered " + confirmed.status() + " for scenario " + scenarioId + ".");
    }
}
