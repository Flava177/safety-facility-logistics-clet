package gh.edu.clet.sfl.facilities.shared.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Role to facilities-permission mapping for the SRS S152 and S153 user classes.
 *
 * <p>{@code SiteScopedPrincipal} carries roles and site scopes but no permissions, so permissions are
 * derived here rather than read from a token claim. Keeping the derivation in the facilities service
 * — not in {@code sfl-service-common} — means no IFIMP business rule leaks into the shared library,
 * and the matrix can be replaced by real token claims later without touching a single call site
 * (gap report C-07). Same decision as {@code FleetPermissionMatrix}, {@code FuelPermissionMatrix},
 * {@code DispatchPermissionMatrix} and {@code EmergencyPermissionMatrix}.
 *
 * <p>It lives in {@code shared} rather than in {@code masterdata} because S152 is the host platform:
 * {@code readiness}, {@code dashboard}, S153 {@code maintenance} and S159 {@code booking} all
 * authorise against this one matrix. A per-module matrix is how five modules end up disagreeing about
 * what a facilities manager may do.
 *
 * <p>The interesting grants, none of them incidental:
 * <ul>
 *   <li>{@link SflRole#IFIMP_TECHNICIAN} assesses readiness, changes an asset's operational status
 *       and works the jobs assigned to them — but cannot manage the estate, override a lock, change
 *       the operating mode, or <em>close</em> a work order. A technician marks work complete; a
 *       supervisor accepts it.</li>
 *   <li>{@link SflRole#VENDOR_TECHNICIAN} was the same set as the technician until S153 and is now
 *       much narrower: a contractor is not staff, and site scope is the wrong boundary for one. The
 *       real boundary is <strong>assignment</strong>, enforced per record in
 *       {@code WorkOrderApplicationService} because "the ones assigned to me" is a property of the
 *       record and not something a matrix can say.</li>
 *   <li>{@link SflRole#IFIMP_REQUESTER} reports a fault and follows their own, and reads nothing
 *       else — the fault read is narrowed per record the same way. A requester who could read the
 *       site's fault register would learn which halls are unusable and which security equipment is
 *       broken, which is not what reporting a leak earns.</li>
 *   <li>{@link SflPermission#FACILITIES_EVIDENCE_EXPORT} is held only by reviewers and
 *       administrators. SRS-SFL-S153-03 makes export a distinct authorised act with a recorded
 *       reason, not a stronger form of reading.</li>
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
            SflPermission.FACILITIES_DASHBOARD_READ,
            SflPermission.FACILITIES_FAULT_READ,
            SflPermission.FACILITIES_WORK_ORDER_READ,
            // S159. A room diary is the least sensitive thing in this service — knowing that Hall A is
            // taken on Tuesday is what stops two people planning for it — so every staff-facing role
            // that reads the estate reads its bookings. The narrow roles below, which do not take
            // READ_ONLY, are granted or refused it individually.
            SflPermission.FACILITIES_BOOKING_READ,
            SflPermission.FACILITIES_RESOURCE_READ);

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
                SflPermission.FACILITIES_CONFIG_READ,
                SflPermission.FACILITIES_FAULT_REPORT,
                SflPermission.FACILITIES_FAULT_TRIAGE,
                SflPermission.FACILITIES_WORK_ORDER_CREATE,
                SflPermission.FACILITIES_WORK_ORDER_ASSIGN,
                SflPermission.FACILITIES_WORK_ORDER_UPDATE,
                SflPermission.FACILITIES_WORK_ORDER_CLOSE,
                SflPermission.FACILITIES_WORK_ORDER_CANCEL,
                SflPermission.FACILITIES_PM_SCHEDULE_READ,
                SflPermission.FACILITIES_PM_SCHEDULE_MANAGE,
                SflPermission.FACILITIES_VENDOR_READ,
                SflPermission.FACILITIES_VENDOR_MANAGE,
                SflPermission.FACILITIES_EVIDENCE_READ,
                SflPermission.FACILITIES_EVIDENCE_ATTACH,
                SflPermission.FACILITIES_EVIDENCE_EXPORT,
                SflPermission.FACILITIES_BOOKING_REQUEST,
                SflPermission.FACILITIES_BOOKING_APPROVE,
                SflPermission.FACILITIES_BOOKING_CANCEL,
                SflPermission.FACILITIES_BOOKING_OVERRIDE,
                SflPermission.FACILITIES_RESOURCE_MANAGE,
                SflPermission.FACILITIES_SETUP_TASK_MANAGE));

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
                SflPermission.FACILITIES_CONFIG_READ,
                SflPermission.FACILITIES_FAULT_REPORT,
                SflPermission.FACILITIES_FAULT_TRIAGE,
                SflPermission.FACILITIES_WORK_ORDER_CREATE,
                SflPermission.FACILITIES_WORK_ORDER_ASSIGN,
                SflPermission.FACILITIES_WORK_ORDER_UPDATE,
                SflPermission.FACILITIES_WORK_ORDER_CLOSE,
                SflPermission.FACILITIES_WORK_ORDER_CANCEL,
                SflPermission.FACILITIES_PM_SCHEDULE_READ,
                SflPermission.FACILITIES_PM_SCHEDULE_MANAGE,
                SflPermission.FACILITIES_VENDOR_READ,
                SflPermission.FACILITIES_VENDOR_MANAGE,
                SflPermission.FACILITIES_EVIDENCE_READ,
                SflPermission.FACILITIES_EVIDENCE_ATTACH,
                // No BOOKING_OVERRIDE, matching the absence of READINESS_OVERRIDE above. Booking into
                // a space readiness has refused is the same class of decision as declaring it ready
                // anyway, and this role holds neither.
                SflPermission.FACILITIES_BOOKING_REQUEST,
                SflPermission.FACILITIES_BOOKING_APPROVE,
                SflPermission.FACILITIES_BOOKING_CANCEL,
                SflPermission.FACILITIES_RESOURCE_MANAGE,
                SflPermission.FACILITIES_SETUP_TASK_MANAGE));

        // Maintenance supervisor — owns readiness and the asset register it depends on, and can
        // override a lock because a supervisor is who a blocked examination hall escalates to.
        matrix.put(SflRole.IFIMP_MAINTENANCE_SUPERVISOR, union(READ_ONLY,
                SflPermission.FACILITIES_SPACE_MANAGE,
                SflPermission.FACILITIES_ASSET_MANAGE,
                SflPermission.FACILITIES_DEVICE_REFERENCE_REGISTER,
                SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_READINESS_OVERRIDE,
                SflPermission.FACILITIES_READINESS_CHECKLIST_MANAGE,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN,
                SflPermission.FACILITIES_FAULT_REPORT,
                SflPermission.FACILITIES_FAULT_TRIAGE,
                SflPermission.FACILITIES_WORK_ORDER_CREATE,
                SflPermission.FACILITIES_WORK_ORDER_ASSIGN,
                SflPermission.FACILITIES_WORK_ORDER_UPDATE,
                SflPermission.FACILITIES_WORK_ORDER_CLOSE,
                SflPermission.FACILITIES_WORK_ORDER_CANCEL,
                SflPermission.FACILITIES_PM_SCHEDULE_READ,
                SflPermission.FACILITIES_PM_SCHEDULE_MANAGE,
                SflPermission.FACILITIES_VENDOR_READ,
                SflPermission.FACILITIES_EVIDENCE_READ,
                SflPermission.FACILITIES_EVIDENCE_ATTACH,
                // Books spaces for maintenance access — the RESERVED purpose — and runs the setups.
                //
                // Deliberately no BOOKING_OVERRIDE, even though this role holds READINESS_OVERRIDE.
                // The two would be redundant and the redundancy is harmful: a supervisor who needs a
                // blocked hall used should clear or downgrade the blocker, which leaves a readiness
                // record somebody can review, rather than book past it and leave the hall still
                // reading BLOCKED to everyone else.
                SflPermission.FACILITIES_BOOKING_REQUEST,
                SflPermission.FACILITIES_BOOKING_CANCEL,
                SflPermission.FACILITIES_RESOURCE_MANAGE,
                SflPermission.FACILITIES_SETUP_TASK_MANAGE));

        // In-house technician — field work. Assesses readiness, changes asset status, works the jobs
        // assigned to them.
        //
        // No CREATE and no ASSIGN: a technician who could raise and self-assign work would be outside
        // the queue the supervisor is accountable for. And no CLOSE, which is the more interesting
        // omission: a technician marks work COMPLETED and a supervisor accepts it. Giving them both
        // would collapse the two states the SRS separates ("Authorised user closes or verifies
        // closure") into one, and would let the person who did the job be the only person who ever
        // saw it — which is exactly what closure evidence exists to prevent.
        matrix.put(SflRole.IFIMP_TECHNICIAN, union(READ_ONLY,
                SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_ASSET_MANAGE,
                SflPermission.FACILITIES_FAULT_REPORT,
                SflPermission.FACILITIES_WORK_ORDER_UPDATE,
                SflPermission.FACILITIES_PM_SCHEDULE_READ,
                SflPermission.FACILITIES_EVIDENCE_READ,
                SflPermission.FACILITIES_EVIDENCE_ATTACH,
                // Turns rooms around before bookings. Not a booker: a technician who could reserve a
                // hall would be scheduling the estate from the shop floor.
                SflPermission.FACILITIES_SETUP_TASK_MANAGE));

        // Vendor technician — a contractor, and therefore NOT a technician with a different badge.
        //
        // This is the narrowest role in the matrix and the split from IFIMP_TECHNICIAN is deliberate:
        // the two shared a permission set before S153, which meant a contractor could read the whole
        // estate register, every fault at the site and every asset's condition. Site scope is the
        // wrong boundary for somebody who is not CLET staff.
        //
        // The permissions below are the outer bound; the real boundary is **assignment**, enforced
        // per record in WorkOrderApplicationService rather than here, because "the ones assigned to
        // me" is not a fact a role-to-permission table can express. A vendor sees the work orders
        // assigned to them and nothing else, and reads only the spaces and assets those touch.
        //
        // No FACILITIES_ASSET_MANAGE: a contractor reporting that a generator is now out of service
        // does it by completing the work order, which is reviewed, rather than by editing the asset
        // register directly and changing a hall's readiness on their own authority.
        matrix.put(SflRole.VENDOR_TECHNICIAN, EnumSet.of(
                SflPermission.FACILITIES_SITE_READ,
                SflPermission.FACILITIES_SPACE_READ,
                SflPermission.FACILITIES_ASSET_READ,
                SflPermission.FACILITIES_WORK_ORDER_READ,
                SflPermission.FACILITIES_WORK_ORDER_UPDATE,
                SflPermission.FACILITIES_EVIDENCE_ATTACH,
                SflPermission.FACILITIES_EVIDENCE_READ));

        // Requester — reports what they can see is wrong, and follows their own report. Nothing more.
        // FACILITIES_FAULT_READ is granted, and narrowed per record to the faults they reported: a
        // requester who could read the site's whole fault register would learn which halls are
        // unusable and which security equipment is broken, which is not what reporting a leak earns.
        //
        // S159 makes this the busiest role in the module rather than the narrowest: a requester is
        // exactly the person who books a room. BOOKING_READ is granted and narrowed per record to
        // their own bookings, the same treatment FACILITIES_FAULT_READ gets and for the same reason —
        // a full room diary would tell somebody which halls are empty and when.
        //
        // No BOOKING_CANCEL: cancelling one's own booking is allowed by the per-record rule in
        // BookingApplicationService, and the permission is what it takes to cancel somebody else's.
        matrix.put(SflRole.IFIMP_REQUESTER, EnumSet.of(
                SflPermission.FACILITIES_SITE_READ,
                SflPermission.FACILITIES_SPACE_READ,
                SflPermission.FACILITIES_FAULT_REPORT,
                SflPermission.FACILITIES_FAULT_READ,
                SflPermission.FACILITIES_BOOKING_READ,
                SflPermission.FACILITIES_BOOKING_REQUEST,
                SflPermission.FACILITIES_RESOURCE_READ));

        // Command — oversight across facilities and emergency; declares examination mode.
        matrix.put(SflRole.COMMAND_ROLE, union(READ_ONLY,
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE,
                SflPermission.FACILITIES_READINESS_OVERRIDE,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN,
                SflPermission.FACILITIES_AUDIT_READ,
                SflPermission.FACILITIES_PM_SCHEDULE_READ,
                SflPermission.FACILITIES_EVIDENCE_READ,
                SflPermission.FACILITIES_BOOKING_APPROVE,
                SflPermission.FACILITIES_BOOKING_CANCEL,
                SflPermission.FACILITIES_BOOKING_OVERRIDE));

        // Centre manager — runs a centre, so declares its mode, reads its readiness, and owns its
        // diary. The role S159 expects to hold BOOKING_OVERRIDE in practice: deciding that an
        // examination will go ahead in a degraded hall is a centre-level operational call, made with
        // a recorded reason, and it is the same authority NFR 23.3 already gives this role over mode.
        matrix.put(SflRole.CENTRE_MANAGER, union(READ_ONLY,
                SflPermission.FACILITIES_OPERATING_MODE_CHANGE,
                SflPermission.FACILITIES_READINESS_ASSESS,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN,
                SflPermission.FACILITIES_BOOKING_REQUEST,
                SflPermission.FACILITIES_BOOKING_APPROVE,
                SflPermission.FACILITIES_BOOKING_CANCEL,
                SflPermission.FACILITIES_BOOKING_OVERRIDE,
                SflPermission.FACILITIES_SETUP_TASK_MANAGE));

        // Read-and-prove roles. Breadth is cheap because they change nothing.
        Set<SflPermission> assurance = union(READ_ONLY,
                SflPermission.FACILITIES_DASHBOARD_DRILLDOWN,
                SflPermission.FACILITIES_AUDIT_READ,
                SflPermission.FACILITIES_AUDIT_INTEGRITY_CHECK,
                SflPermission.FACILITIES_CONFIG_READ,
                SflPermission.FACILITIES_PM_SCHEDULE_READ,
                SflPermission.FACILITIES_VENDOR_READ,
                SflPermission.FACILITIES_EVIDENCE_READ,
                // Export is the assurance function, not a stronger form of reading. SRS-SFL-S153-03
                // requires an approved reason with every export and audits the act itself, which is
                // why no operational role holds this and every holder of it is a reviewer.
                SflPermission.FACILITIES_EVIDENCE_EXPORT);
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
