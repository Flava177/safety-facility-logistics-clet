package gh.edu.clet.sfl.facilities.spaceplanning.support;

import gh.edu.clet.sfl.facilities.spaceplanning.application.ports.ConstructionHandoffPort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A double for S176's intake, standing in for the real S176 module which is built in another worktree.
 * Records every proposal so a test can assert what S158 sent, and can be made to look unavailable.
 */
public class FakeConstructionHandoffPort implements ConstructionHandoffPort {

    public final List<Proposal> proposals = new ArrayList<>();
    private boolean unavailable;

    public void makeUnavailable() {
        unavailable = true;
    }

    @Override
    public ProposedProject propose(Proposal proposal) {
        if (unavailable) {
            throw new ConstructionUnavailableException("S176 is not available in this test", null);
        }
        proposals.add(proposal);
        String reference = "PRJ-" + proposal.siteCode() + "-" + String.format("%06d", proposals.size());
        return new ProposedProject(UUID.randomUUID(), reference, "REGISTERED");
    }
}
