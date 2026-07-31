package gh.edu.clet.sfl.facilities.maintenance.infrastructure.persistence;

/**
 * Marker for the four smaller Spring Data repositories in this package.
 *
 * <p>They are separate top-level files rather than interfaces nested in a holder, because Spring
 * Data does not scan nested interfaces — a repository declared inside a class compiles, starts, and
 * is silently never implemented. That cost the S152 round an afternoon and is worth a comment
 * somebody will read before trying it again.
 *
 * @see JpaWorkOrderPartRepository
 * @see JpaMaintenanceEvidenceRepository
 * @see JpaMaintenanceVendorRepository
 * @see JpaPreventiveScheduleRepository
 */
final class JpaMaintenanceSupportRepositories {

    private JpaMaintenanceSupportRepositories() {
    }
}
