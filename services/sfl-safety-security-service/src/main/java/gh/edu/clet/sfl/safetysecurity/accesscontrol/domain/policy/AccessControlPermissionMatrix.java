package gh.edu.clet.sfl.safetysecurity.accesscontrol.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Additive S160a role -> permission mapping, following {@code VisitorPermissionMatrix}'s shape. */
public final class AccessControlPermissionMatrix {

    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();

    private AccessControlPermissionMatrix() {
    }

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole, Set<SflPermission>> m = new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all = EnumSet.noneOf(SflPermission.class);
        for (var p : SflPermission.values()) {
            if (p.name().startsWith("ACCESS_")) {
                all.add(p);
            }
        }
        m.put(SflRole.SFL_ADMIN, all);
        m.put(SflRole.SECURITY_DIRECTOR, EnumSet.copyOf(all));

        // Access Control Administrator (S160a-05 user story): owns zones, door groups, schedules and
        // provisioning; does not run the SOC queue day to day.
        m.put(SflRole.ACCESS_CONTROL_ADMINISTRATOR, EnumSet.of(SflPermission.ACCESS_ZONE_MANAGE,
                SflPermission.ACCESS_ZONE_READ, SflPermission.ACCESS_PROVISIONING_MANAGE,
                SflPermission.ACCESS_PROVISIONING_READ, SflPermission.ACCESS_EVENT_READ,
                SflPermission.ACCESS_REPORT_READ));

        // SOC Operator (S160a-03/04 user stories): overrides, exception triage, occupancy - not zone
        // definitions.
        m.put(SflRole.SOC_OPERATOR, EnumSet.of(SflPermission.ACCESS_EVENT_READ, SflPermission.ACCESS_EXCEPTION_READ,
                SflPermission.ACCESS_EXCEPTION_ACKNOWLEDGE, SflPermission.ACCESS_OVERRIDE_CREATE,
                SflPermission.ACCESS_OVERRIDE_BREAK_GLASS, SflPermission.ACCESS_OVERRIDE_READ,
                SflPermission.ACCESS_OCCUPANCY_READ, SflPermission.ACCESS_ZONE_READ));

        // Integration Engineer: reads what has been ingested; never approves overrides or manages zones.
        m.put(SflRole.INTEGRATION_ENGINEER, EnumSet.of(SflPermission.ACCESS_EVENT_READ,
                SflPermission.ACCESS_EXCEPTION_READ, SflPermission.ACCESS_ZONE_READ));

        // Emergency Coordinator (S160a-06 user story): occupancy/muster only.
        m.put(SflRole.EMERGENCY_COORDINATOR, EnumSet.of(SflPermission.ACCESS_OCCUPANCY_READ));

        m.put(SflRole.AUDITOR, EnumSet.of(SflPermission.ACCESS_EVENT_READ, SflPermission.ACCESS_EXCEPTION_READ,
                SflPermission.ACCESS_PROVISIONING_READ, SflPermission.ACCESS_OVERRIDE_READ,
                SflPermission.ACCESS_ZONE_READ, SflPermission.ACCESS_REPORT_READ));

        m.put(SflRole.COMPLIANCE_OFFICER, EnumSet.of(SflPermission.ACCESS_EVENT_READ,
                SflPermission.ACCESS_REPORT_READ, SflPermission.ACCESS_OVERRIDE_READ));

        m.replaceAll((r, p) -> Set.copyOf(p));
        return Map.copyOf(m);
    }
}
