package gh.edu.clet.sfl.fleetlogistics.fleet.application.command;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.LicenceClass;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.time.LocalDate;
import java.util.Map;

/** Registers an approved driver profile reference (SRS-SFL-S166-01). */
public record RegisterDriverCommand(
        String staffReference,
        String displayName,
        String licenceNumber,
        LicenceClass licenceClass,
        LocalDate licenceExpiresOn,
        LocalDate medicalClearanceExpiresOn,
        String siteCode,
        String responsibleUnit,
        /**
         * The identity provider subject that will sign in as this driver, or null.
         *
         * <p>Optional, and left out of {@link #idempotencyPayload()} deliberately: binding a profile to
         * a person is a correction that should be able to follow a registration, and including it here
         * would make a re-send that adds the binding look like a different request.
         */
        String principalSubject,
        ActorContext actor,
        SourceChannel sourceChannel,
        String idempotencyKey) implements FleetCommand {

    public Map<String, Object> idempotencyPayload() {
        return Map.of(
                "staffReference", String.valueOf(staffReference),
                "siteCode", String.valueOf(siteCode),
                "licenceNumber", String.valueOf(licenceNumber),
                "licenceClass", String.valueOf(licenceClass),
                "licenceExpiresOn", String.valueOf(licenceExpiresOn));
    }
}
