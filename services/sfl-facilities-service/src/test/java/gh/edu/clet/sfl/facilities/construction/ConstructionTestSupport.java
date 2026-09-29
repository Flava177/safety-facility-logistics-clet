package gh.edu.clet.sfl.facilities.construction;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.construction.application.ports.ScenarioConfirmationPort;
import gh.edu.clet.sfl.facilities.construction.application.ports.SiteAccessPort;
import gh.edu.clet.sfl.facilities.construction.domain.Contractor;
import gh.edu.clet.sfl.facilities.construction.domain.SiteAccessGrant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Test doubles for the two S176 ports with no real counterpart built anywhere in this workspace:
 * S160a/S160 (site access) and S158 (scenario confirmation, before that module is merged).
 *
 * <p>S152 ({@code EstateRegisterPort}) and S153 ({@code DefectWorkOrderPort}) are exercised for real,
 * through {@code IfimpTestHarness.estate}/{@code intake} and the module's own adapters - see
 * {@code S176MandatoryScenariosTest} - because both are built in this workspace and a double of them
 * would only prove the call was made.
 */
public final class ConstructionTestSupport {

    private ConstructionTestSupport() {
    }

    /** Records every request and suspension, exactly as {@code OutboxSiteAccessAdapter} does for real. */
    public static final class RecordingSiteAccessPort implements SiteAccessPort {

        public final List<UUID> requested = new ArrayList<>();
        public final List<UUID> suspended = new ArrayList<>();

        @Override
        public Dispatch requestAccess(SiteAccessGrant grant, Contractor contractor, ActorContext actor) {
            requested.add(grant.id());
            return new Dispatch("TEST-RECORDED-NOT-ENFORCED", false);
        }

        @Override
        public Dispatch suspendAccess(SiteAccessGrant grant, Contractor contractor, String reason,
                ActorContext actor) {
            suspended.add(grant.id());
            return new Dispatch("TEST-RECORDED-NOT-ENFORCED", false);
        }
    }

    /**
     * Stands in for S158, which is being built in another worktree. By default behaves exactly as the
     * real workspace does until S158 is merged: every scenario is unknown, so confirmation is
     * UNRESOLVED. {@link #knownScenarios} lets a test name a scenario S158 would confirm.
     */
    public static final class StubScenarioConfirmationPort implements ScenarioConfirmationPort {

        public final Set<UUID> knownScenarios = new HashSet<>();
        public final List<UUID> confirmed = new ArrayList<>();

        @Override
        public Result confirm(UUID scenarioId, UUID projectId, String projectReference, String confirmedBy) {
            confirmed.add(scenarioId);
            if (knownScenarios.contains(scenarioId)) {
                return new Result(true, null);
            }
            return new Result(false, "S158 has no record of scenario " + scenarioId + ".");
        }
    }
}
