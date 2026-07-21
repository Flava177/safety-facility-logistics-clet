package gh.edu.clet.sfl.fleetlogistics.fleet.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Role to fleet-permission mapping for the SRS S166 user classes.
 *
 * <p>{@code SiteScopedPrincipal} carries roles and site scopes but no permissions, so permissions are
 * derived here rather than read from a token claim. Keeping the derivation in the fleet feature — not in
 * {@code sfl-service-common} — means no fleet business rule leaks into the shared library, and the matrix
 * can be replaced by real token claims later without touching call sites (gap report C-07).
 *
 * <p>The SRS user classes map to roles as follows:
 * <ul>
 *   <li>Fleet or Logistics Officer — {@link SflRole#FLEET_LOGISTICS_OFFICER}</li>
 *   <li>Fleet Manager — {@link SflRole#FLEET_MANAGER}</li>
 *   <li>Driver / limited mobile user — {@link SflRole#FLEET_DRIVER}</li>
 *   <li>Auditor — {@link SflRole#AUDITOR}; Compliance Officer — {@link SflRole#COMPLIANCE_OFFICER}</li>
 *   <li>System Administrator — {@link SflRole#SFL_ADMIN}, {@link SflRole#DTI_ADMIN}</li>
 *   <li>Read-only management/reporting — {@link SflRole#FLEET_REPORTING_VIEWER}, {@link SflRole#COMMAND_ROLE}</li>
 *   <li>Service integration principal — {@link SflRole#SERVICE_INTEGRATION}, {@link SflRole#INTEGRATION_ENGINEER}</li>
 * </ul>
 */
public final class FleetPermissionMatrix {

    private static final Set<SflPermission> READ_ONLY = EnumSet.of(
            SflPermission.FLEET_VEHICLE_READ,
            SflPermission.FLEET_DRIVER_READ,
            SflPermission.FLEET_TRIP_READ,
            SflPermission.FLEET_WORKFLOW_READ,
            SflPermission.FLEET_DASHBOARD_READ);

    private static final Map<SflRole, Set<SflPermission>> MATRIX = buildMatrix();

    private FleetPermissionMatrix() {
    }

    /** Permissions granted by a single role. */
    public static Set<SflPermission> permissionsFor(SflRole role) {
        return MATRIX.getOrDefault(role, Set.of());
    }

    /** The union of permissions granted by all of an actor's roles. */
    public static Set<SflPermission> permissionsFor(Set<SflRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        EnumSet<SflPermission> granted = EnumSet.noneOf(SflPermission.class);
        roles.forEach(role -> granted.addAll(permissionsFor(role)));
        return Set.copyOf(granted);
    }

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        if (roles == null || permission == null) {
            return false;
        }
        return roles.stream().anyMatch(role -> permissionsFor(role).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> buildMatrix() {
        Map<SflRole, Set<SflPermission>> matrix = new EnumMap<>(SflRole.class);

        // Platform administrator: every fleet capability, including break-glass overrides.
        matrix.put(SflRole.SFL_ADMIN, EnumSet.allOf(SflPermission.class).stream()
                .filter(permission -> permission.name().startsWith("FLEET_"))
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(SflPermission.class))));

        // Fleet Manager: full operational ownership plus privileged transitions and approvals.
        matrix.put(SflRole.FLEET_MANAGER, EnumSet.of(
                SflPermission.FLEET_VEHICLE_READ,
                SflPermission.FLEET_VEHICLE_MANAGE,
                SflPermission.FLEET_VEHICLE_LIFECYCLE_MANAGE,
                SflPermission.FLEET_VEHICLE_RESTORE,
                SflPermission.FLEET_VEHICLE_SENSITIVE_READ,
                SflPermission.FLEET_VEHICLE_ODOMETER_CORRECT,
                SflPermission.FLEET_COMPLIANCE_MANAGE,
                SflPermission.FLEET_SERVICE_RECORD_MANAGE,
                SflPermission.FLEET_DRIVER_READ,
                SflPermission.FLEET_DRIVER_MANAGE,
                SflPermission.FLEET_DRIVER_SENSITIVE_READ,
                SflPermission.FLEET_TRIP_READ,
                SflPermission.FLEET_TRIP_MANAGE,
                SflPermission.FLEET_TRIP_ASSIGN,
                SflPermission.FLEET_TRIP_CANCEL,
                SflPermission.FLEET_TRIP_CLOSE,
                SflPermission.FLEET_INSPECTION_RECORD,
                SflPermission.FLEET_WORKFLOW_READ,
                SflPermission.FLEET_WORKFLOW_MANAGE,
                SflPermission.FLEET_WORKFLOW_ASSIGN,
                SflPermission.FLEET_WORKFLOW_ESCALATE,
                SflPermission.FLEET_WORKFLOW_APPROVE,
                SflPermission.FLEET_WORKFLOW_CANCEL,
                SflPermission.FLEET_WORKFLOW_REOPEN,
                SflPermission.FLEET_EVIDENCE_READ,
                SflPermission.FLEET_EVIDENCE_REGISTER,
                SflPermission.FLEET_EVIDENCE_EXPORT_REQUEST,
                SflPermission.FLEET_DASHBOARD_READ,
                SflPermission.FLEET_DASHBOARD_DRILLDOWN,
                SflPermission.FLEET_REPORT_EXPORT,
                SflPermission.FLEET_INTEGRATION_HEALTH_READ));

        // Fleet or Logistics Officer: day-to-day register and queue work, no privileged overrides.
        matrix.put(SflRole.FLEET_LOGISTICS_OFFICER, EnumSet.of(
                SflPermission.FLEET_VEHICLE_READ,
                SflPermission.FLEET_VEHICLE_MANAGE,
                SflPermission.FLEET_COMPLIANCE_MANAGE,
                SflPermission.FLEET_SERVICE_RECORD_MANAGE,
                SflPermission.FLEET_DRIVER_READ,
                SflPermission.FLEET_DRIVER_MANAGE,
                SflPermission.FLEET_TRIP_READ,
                SflPermission.FLEET_TRIP_MANAGE,
                SflPermission.FLEET_TRIP_ASSIGN,
                SflPermission.FLEET_TRIP_CLOSE,
                SflPermission.FLEET_INSPECTION_RECORD,
                SflPermission.FLEET_WORKFLOW_READ,
                SflPermission.FLEET_WORKFLOW_MANAGE,
                SflPermission.FLEET_WORKFLOW_ASSIGN,
                SflPermission.FLEET_EVIDENCE_READ,
                SflPermission.FLEET_EVIDENCE_REGISTER,
                SflPermission.FLEET_DASHBOARD_READ,
                SflPermission.FLEET_DASHBOARD_DRILLDOWN));

        // Driver / limited mobile user: sees the work assigned to them and records inspections.
        matrix.put(SflRole.FLEET_DRIVER, EnumSet.of(
                SflPermission.FLEET_VEHICLE_READ,
                SflPermission.FLEET_TRIP_READ,
                SflPermission.FLEET_INSPECTION_RECORD,
                SflPermission.FLEET_EVIDENCE_REGISTER));

        // Auditor: read everything in scope, replay the audit chain, request exports.
        matrix.put(SflRole.AUDITOR, union(READ_ONLY, EnumSet.of(
                SflPermission.FLEET_EVIDENCE_READ,
                SflPermission.FLEET_EVIDENCE_EXPORT_REQUEST,
                SflPermission.FLEET_AUDIT_READ,
                SflPermission.FLEET_AUDIT_INTEGRITY_CHECK,
                SflPermission.FLEET_DASHBOARD_DRILLDOWN,
                SflPermission.FLEET_INTEGRATION_HEALTH_READ)));

        // Compliance Officer: the auditor view plus export approval and legal-hold override.
        matrix.put(SflRole.COMPLIANCE_OFFICER, union(matrix.get(SflRole.AUDITOR), EnumSet.of(
                SflPermission.FLEET_EVIDENCE_EXPORT_APPROVE,
                SflPermission.FLEET_EVIDENCE_LEGAL_HOLD_OVERRIDE,
                SflPermission.FLEET_REPORT_EXPORT)));

        // Read-only management and reporting.
        matrix.put(SflRole.FLEET_REPORTING_VIEWER, union(READ_ONLY, EnumSet.of(
                SflPermission.FLEET_DASHBOARD_DRILLDOWN)));
        matrix.put(SflRole.COMMAND_ROLE, union(READ_ONLY, EnumSet.of(
                SflPermission.FLEET_DASHBOARD_DRILLDOWN,
                SflPermission.FLEET_INTEGRATION_HEALTH_READ)));

        // System administration and integration operation.
        matrix.put(SflRole.DTI_ADMIN, union(READ_ONLY, EnumSet.of(
                SflPermission.FLEET_INTEGRATION_HEALTH_READ,
                SflPermission.FLEET_INTEGRATION_REPLAY,
                SflPermission.FLEET_AUDIT_READ,
                SflPermission.FLEET_AUDIT_INTEGRITY_CHECK)));
        matrix.put(SflRole.INTEGRATION_ENGINEER, EnumSet.of(
                SflPermission.FLEET_INTEGRATION_INGEST,
                SflPermission.FLEET_INTEGRATION_HEALTH_READ,
                SflPermission.FLEET_INTEGRATION_REPLAY));

        // Service principal used by vendor webhooks and other SFL services.
        matrix.put(SflRole.SERVICE_INTEGRATION, EnumSet.of(
                SflPermission.FLEET_INTEGRATION_INGEST,
                SflPermission.FLEET_INTEGRATION_HEALTH_READ));

        matrix.replaceAll((role, permissions) -> Set.copyOf(permissions));
        return Map.copyOf(matrix);
    }

    private static Set<SflPermission> union(Set<SflPermission> first, Set<SflPermission> second) {
        EnumSet<SflPermission> merged = EnumSet.noneOf(SflPermission.class);
        merged.addAll(first);
        merged.addAll(second);
        return merged;
    }
}
