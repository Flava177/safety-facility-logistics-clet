package gh.edu.clet.sfl.facilities.spaceplanning.infrastructure.integration;

import gh.edu.clet.sfl.facilities.construction.application.contract.ConstructionProjectIntake;
import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.ConstructionHandoffPort;
import org.springframework.stereotype.Component;

/**
 * S158's hand-off to S176, over S176's published {@link ConstructionProjectIntake} contract - the only
 * S158 class that names an S176 type, and it names only the contract (SRS-SFL-S158-01/-04).
 *
 * <p>In a build without S176 the contract is served by a scaffold that throws {@link IllegalStateException};
 * that, and any other failure of the intake, becomes {@link ConstructionUnavailableException} so the
 * caller refuses the commit or hand-off cleanly instead of recording a project that does not exist.
 */
@Component
public class S176ConstructionHandoffAdapter implements ConstructionHandoffPort {

    private final ConstructionProjectIntake intake;

    public S176ConstructionHandoffAdapter(ConstructionProjectIntake intake) {
        this.intake = intake;
    }

    @Override
    public ProposedProject propose(Proposal proposal) {
        ConstructionProjectIntake.ProposedProject project;
        try {
            project = intake.proposeFromSpaceChange(new ConstructionProjectIntake.SpaceChangeProposal(
                    proposal.spaceChangeRequestId(), proposal.siteCode(), proposal.title(), proposal.scope(),
                    proposal.requestingUnit(), proposal.justification(), proposal.committedScenarioId(),
                    proposal.roomIds(), proposal.requestedBy()));
        } catch (IllegalStateException | UnsupportedOperationException unavailable) {
            throw new ConstructionUnavailableException("S176 construction project intake is not available", unavailable);
        }
        if (project == null || project.projectId() == null) {
            throw new ConstructionUnavailableException("S176 returned no project for the proposal", null);
        }
        return new ProposedProject(project.projectId(), project.projectReference(), project.status());
    }
}
