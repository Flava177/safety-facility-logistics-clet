package gh.edu.clet.sfl.facilities.construction.application.contract;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * How S158 hands an approved space change that needs physical works to S176 - SRS-SFL-S158-04.
 *
 * <p>"When actioned, then an S176 project reference is created and linked back to the request." The
 * project is registered in S176's own lifecycle - not approved, not started - so every S176 gate
 * (approval sign-off, permits, budget) still applies; S158 only supplies its origin.
 */
public interface ConstructionProjectIntake {

    ProposedProject proposeFromSpaceChange(SpaceChangeProposal proposal);

    Optional<ProposedProject> find(UUID projectId);

    /**
     * @param committedScenarioId the S158 scenario the works will realise, if one exists yet - S176-04
     *        confirms it at handover
     */
    record SpaceChangeProposal(UUID spaceChangeRequestId, String siteCode, String title, String scope,
            String requestingUnit, String justification, UUID committedScenarioId, List<UUID> roomIds,
            String requestedBy) {
    }

    record ProposedProject(UUID projectId, String projectReference, String status) {
    }
}
