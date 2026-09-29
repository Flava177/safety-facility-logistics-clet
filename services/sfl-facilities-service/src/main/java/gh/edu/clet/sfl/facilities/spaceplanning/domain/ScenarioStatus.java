package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where an allocation scenario is in its life - SRS-SFL-S158-01.
 *
 * <p>The one distinction every rule in S158 turns on is {@link #DRAFT} against everything else. A draft
 * is a "what-if" and must never be read as current state (the "Uncommitted Scenario Referenced" error
 * state); only a committed scenario can change the S152 register, and it does so once.
 *
 * <ul>
 *   <li>{@link #DRAFT} - being modelled. Editable, comparable, never current.</li>
 *   <li>{@link #COMMITTED} - the explicit, named, audited decision has been taken. A like-for-like
 *       commit has been applied to S152 in the same transaction; a physical-works commit is waiting for
 *       its S176 project to hand over.</li>
 *   <li>{@link #HANDED_OVER} - S176 confirmed the works and the allocations were applied. Terminal.</li>
 *   <li>{@link #DISCARDED} - abandoned without being committed. Terminal, kept for the record.</li>
 * </ul>
 */
public enum ScenarioStatus {
    DRAFT,
    COMMITTED,
    HANDED_OVER,
    DISCARDED;

    public boolean isDraft() {
        return this == DRAFT;
    }

    /** Committed or handed over - a decision was taken on it. */
    public boolean isCommitted() {
        return this == COMMITTED || this == HANDED_OVER;
    }

    public Set<ScenarioStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> EnumSet.of(COMMITTED, DISCARDED);
            case COMMITTED -> EnumSet.of(HANDED_OVER);
            case HANDED_OVER, DISCARDED -> EnumSet.noneOf(ScenarioStatus.class);
        };
    }

    public boolean canTransitionTo(ScenarioStatus target) {
        return allowedTransitions().contains(target);
    }
}
