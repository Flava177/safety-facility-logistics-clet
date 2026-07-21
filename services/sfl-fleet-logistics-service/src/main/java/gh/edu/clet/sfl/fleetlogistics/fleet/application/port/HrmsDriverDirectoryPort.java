package gh.edu.clet.sfl.fleetlogistics.fleet.application.port;

import java.time.LocalDate;
import java.util.Optional;

/**
 * HRMS driver/staff directory (SRS-SFL-S166-04 "HRMS driver/staff records").
 *
 * <p>A lookup, never a copy: fleet confirms the staff reference exists and is employed, and holds only
 * the operational subset it needs. Copying the personnel record here would create a second source of
 * truth that immediately starts diverging.
 */
public interface HrmsDriverDirectoryPort {

    /**
     * Confirms the staff reference exists and is currently employed at the site.
     *
     * @throws gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.RecordNotFoundException when the
     *         reference is unknown or the person is no longer employed
     * @throws gh.edu.clet.sfl.fleetlogistics.fleet.domain.exception.IntegrationConfigurationNotFoundException
     *         when no HRMS adapter is configured — never a silent pass
     */
    void requireEmployedStaff(String staffReference, String siteCode);

    /** The directory entry, if the reference is known. */
    Optional<StaffDirectoryEntry> findStaff(String staffReference);

    /**
     * The operational subset of an HRMS record.
     *
     * @param employmentEndsOn the last day of employment, or {@code null} for open-ended employment
     */
    record StaffDirectoryEntry(
            String staffReference,
            String displayName,
            String siteCode,
            String unit,
            boolean employed,
            LocalDate employmentEndsOn) {
    }
}
