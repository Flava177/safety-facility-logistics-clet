package gh.edu.clet.sfl.facilities.spaceplanning.domain;

import gh.edu.clet.sfl.facilities.masterdata.domain.SpaceType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The result of checking one space's total allocated headcount against its standard - SRS-SFL-S158-02.
 *
 * <p>Compliance is a property of the <em>room</em>, not of one unit's line in it: two units of three
 * people each in a four-person office are over capacity together, and neither line alone shows it.
 *
 * @param reasonCode why the result is what it is, machine-readable: {@code SPACE_STANDARD_NOT_DEFINED}
 *        (the SRS error state), {@code ROOM_MEASURES_UNKNOWN}, {@code OVER_CAPACITY_SHARE},
 *        {@code UNDER_AREA_PER_PERSON}, or {@code WITHIN_STANDARD}
 * @param findings a sentence per criterion checked, for a planner to read
 * @param overridden an approved override covers this room's current allocation
 */
public record RoomCompliance(
        UUID roomId,
        String roomCode,
        SpaceType spaceType,
        int totalHeadcount,
        Integer capacity,
        BigDecimal areaSqm,
        ComplianceStatus status,
        String reasonCode,
        List<String> findings,
        UUID standardId,
        Integer standardVersion,
        boolean overridden) {

    public RoomCompliance {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }

    /**
     * Whether this room is flagged on the planner's and director's views.
     *
     * <p>Non-compliance is recorded, never blocked (S158-02: "operational necessity sometimes overrides
     * planning guidance"), and an approved override clears the flag without changing the computed status
     * - the room is still over its standard, and the dashboard should say so; it is just no longer an
     * unanswered question.
     */
    public boolean flagged() {
        return status == ComplianceStatus.NON_COMPLIANT && !overridden;
    }

    public RoomCompliance withOverride(boolean approved) {
        return new RoomCompliance(roomId, roomCode, spaceType, totalHeadcount, capacity, areaSqm, status, reasonCode,
                findings, standardId, standardVersion, approved);
    }

    /** One line, for the stamp on scenario allocation rows. */
    public String summary() {
        String joined = findings.isEmpty() ? reasonCode : reasonCode + ": " + String.join(" ", findings);
        return joined.length() > 2000 ? joined.substring(0, 2000) : joined;
    }
}
