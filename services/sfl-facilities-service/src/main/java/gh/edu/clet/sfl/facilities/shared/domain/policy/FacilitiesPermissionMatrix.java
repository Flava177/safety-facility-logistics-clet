package gh.edu.clet.sfl.facilities.shared.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Role to facilities-permission mapping for the SRS S152 user classes.
 *
 * <p>{@code SiteScopedPrincipal} carries roles and site scopes but no permissions, so permissions are
 * derived here rather than read from a token claim. Keeping the derivation in the facilities service
 * — not in {@code sfl-service-common} — means no IFIMP business rule leaks into the shared library,
 * and the matrix can be replaced by real token claims later without touching a single call site
 * (gap report C-07). Same decision as {@code FleetPermissionMatrix}, {@code FuelPermissionMatrix},
 * {@code DispatchPermissionMatrix} and {@code EmergencyPermissionMatrix}.
 *
 * <p>It lives in {@code shared} rather than in {@code masterdata} because S152 is the host platform:
 * {@code readiness}, {@code dashboard} and — when they arrive — S153 maintenance and S159 booking all
 * authorise against this one matrix. A per-module matrix is how four modules end up disagreeing about
 * what a facilities manager may do.
 *
 * <p>The interesting grants, none of them incidental:
 * <ul>
 *   <li>{@link SflRole#IFIMP_TECHNICIAN} and {@link SflRole#VENDOR_TECHNICIAN} can <em>assess</em>
 *       readiness and change an asset's operational status — that is the field work — but cannot
 *       manage the estate, override a lock or change the operating mode.</li>
 *   <li>{@link SflRole#IFIMP_REQUESTER} reads spaces and nothing else. A requester needs to name the
 *       room their fault is in; they do not need the asset register.</li>
 *   <li>{@link SflRole#COMMAND_ROLE} and {@link SflRole#CENTRE_MANAGER} hold
 *       {@link SflPermission#FACILITIES_OPERATING_MODE_CHANGE}. Declaring examination mode is a
 *       centre-level operational decision, and NFR 23.3 requires it to be role-restricted.</li>
 *   <li>{@link SflRole#AUDITOR} and {@link SflRole#COMPLIANCE_OFFICER} read everything and change
 *       nothing, and they alone hold {@link SflPermission#FACILITIES_AUDIT_INTEGRITY_CHECK} alongside
 *       the administrators — an integrity failure is escalated to compliance, so compliance must be
 *       able to run the check.</li>
 * </ul>
 */
public final class FacilitiesPermissionMatrix {

    /** What any facilities-facing role can see. */
    private static final Set<SflPermission> READ_ONLY = EnumSet.of(
            SflPermission.FACILITIES_SITE_READ,
            SflPermission.FACILITIES_SPACE_READ,
            SflPermission.FACILITIES_ZONE_READ,
            SflPermission.FACILITIES_DEVICE_REFERENCE_READ,
            SflPermission.FACILITIES_ASSET_READ,
            SflPermission.FACILITIES_READINESS_READ,
            SflPermission.FACILITIES_DASHBOARD_READ);

    private static final Map<SflRole, Set<SflPermission>> MATRIX = buildMatrix();

    private FacilitiesPermissionMatrix() {
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

        // Platform administration — everything S152 defines.
        Set<SflPermission> administrator = EnumSet.allOf(SflPermission.class).stream()
                .filter(permission -> permission.name().startsWith("FACILITIES_"))
                .collect(java.util.stream.Collectors.toCollection(() -> EnumSet.noneOf(SflPermission.class)));
        matrix.put(SflRole.SFL_ADMIN, Set.copyOf(administrator));
        matrix.put(SflRole.DTI_ADMIN, Set.copyOf(administrator));

        // Facilities director — the whole estate, including mode changes and overrides, but not
        // platform configuration, which is an administrative concern.
        matrix.put(SflRole.FACILITIES_DIRECTOR, union(READ_ONLY,
                SflPermission.FACILITIES_SITE_MANAGE,
                SflPermission.FACILITIES_SPACE_MANAGE,
                SflPermission.FACILITIES_ZONE_MANAGE,
                SflPermission.FACILITIES_ASSET_MANAGE,
                SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER,
                SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_READINESS_OVERRIDE,
                SflPermission.FACILITIES_READINESS_CHECKLIST_MANAGE,
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN,
                SflPermission.FACILITIES_AUDIT_READ,
                SflPermission.FACILITIES_CONFIG_READ));

        // Facilities manager — day-to-day estate management. No mode change: declaring an examination
        // is a centre-level decision, not an estate-maintenance one.
        matrix.put(SflRole.FACILITIES_MANAGER, union(READ_ONLY,
                SflPermission.FACILITIES_SITE_MANAGE,
                SflPermission.FACILITIES_SPACE_MANAGE,
                SflPermission.FACILITIES_ZONE_MANAGE,
                SflPermission.FACILITIES_ASSET_MANAGE,
                SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER,
                SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_READINESS_CHECKLIST_MANAGE,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN,
                SflPermission.FACILITIES_CONFIG_READ));

        // Maintenance supervisor — owns readiness and the asset register it depends on, and can
        // override a lock because a supervisor is who a blocked examination hall escalates to.
        matrix.put(SflRole.IFIMP_MAINTENANCE_SUPERVISOR, union(READ_ONLY,
                SflPermission.FACILITIES_SPACE_MANAGE,
                SflPermission.FACILITIES_ASSET_MANAGE,
                SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER,
                SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_READINESS_OVERRIDE,
                SflPermission.FACILITIES_READINESS_CHECKLIST_MANAGE,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN));

        // Technicians — field work. Assess readiness, change asset status, nothing structural.
        Set<SflPermission> technician = union(READ_ONLY,
                SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_ASSET_MANAGE);
        matrix.put(SflRole.IFIMP_TECHNICIAN, technician);
        matrix.put(SflRole.VENDOR_TECHNICIAN, technician);

        // Requester — names the room a fault is in. Nothing more.
        matrix.put(SflRole.IFIMP_REQUESTER, EnumSet.of(
                SflPermission.FACILITIES_SITE_READ,
                SflPermission.FACILITIES_SPACE_READ));

        // Command — oversight across facilities and emergency; declares examination mode.
        matrix.put(SflRole.COMMAND_ROLE, union(READ_ONLY,
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE,
                SflPermission.FACILITIES_READINESS_OVERRIDE,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN,
                SflPermission.FACILITIES_AUDIT_READ));

        // Centre manager — runs a centre, so declares its mode and reads its readiness.
        matrix.put(SflRole.CENTRE_MANAGER, union(READ_ONLY,
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE,
                SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN));

        // Read-and-prove roles. Breadth is cheap because they change nothing.
        Set<SflPermission> assurance = union(READ_ONLY,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN,
                SflPermission.FACILITIES_AUDIT_READ,
                SflPermission.FACILITIES_AUDIT_INTEGRITY_CHECK,
                SflPermission.FACILITIES_CONFIG_READ);
        matrix.put(SflRole.AUDITOR, assurance);
        matrix.put(SflRole.COMPLIANCE_OFFICER, assurance);

        // Integration principals — maintain the feeds that carry device and asset data in, and need
        // to see whether what they sent landed. They do not operate the estate.
        Set<SflPermission> integration = union(READ_ONLY,
                SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER,
                SflPermission.FACILITIES_CONFIG_READ);
        matrix.put(SflRole.INTEGRATION_ENGINEER, union(integration, SflPermission.FACILITIES_CONFIG_MANAGE));
        matrix.put(SflRole.SERVICE_INTEGRATION, integration);

        // HSE manager — reads the estate to place an incident and judge a location's standing.
        matrix.put(SflRole.HSE_MANAGER, union(READ_ONLY, SflPermission.FACILITIES_DASHBOARD_DRILLDOWN));

        return Map.copyOf(matrix);
    }

    private static Set<SflPermission> union(Set<SflPermission> base, SflPermission... extra) {
        EnumSet<SflPermission> combined = EnumSet.copyOf(base);
        combined.addAll(Set.of(extra));
        return Set.copyOf(combined);
    }
}
