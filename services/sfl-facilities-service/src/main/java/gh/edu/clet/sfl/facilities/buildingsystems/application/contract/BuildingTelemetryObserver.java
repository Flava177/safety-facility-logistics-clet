package gh.edu.clet.sfl.facilities.buildingsystems.application.contract;

/**
 * Something that consumes S156's normalised stream instead of opening its own vendor connection.
 *
 * <p>SRS-SFL-S157-04: "one authenticated ingestion boundary, not two competing ones for the same
 * devices". S156 owns ingestion; S157 implements this. Called for every accepted reading, in S156's
 * transaction - an observer must be quick and must not call back into S156.
 */
public interface BuildingTelemetryObserver {

    void readingAccepted(NormalisedTelemetryReading reading);
}
