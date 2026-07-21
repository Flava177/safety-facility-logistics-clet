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
