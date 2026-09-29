package gh.edu.clet.sfl.facilities.spaceplanning.domain;

/**
 * What committing a scenario sets in motion - SRS-SFL-S158-01: "commits chosen scenario - triggers S152
 * update or S176 project".
 *
 * <p>Chosen by the committing officer, not inferred. Whether a reorganisation needs a wall moving is a
 * judgement nothing in the scenario can make, and guessing wrong in either direction is expensive: a
 * like-for-like guess on a job that needs works would put a unit into a room that is not ready for it.
 */
public enum CommitOutcome {
    /** Reassignment of existing space as it stands. Applied to the S152 register at commit. */
    LIKE_FOR_LIKE,
    /** Needs physical works first. An S176 project is proposed; S152 is updated at S176 handover. */
    PHYSICAL_WORKS
}
