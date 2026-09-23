package gh.edu.clet.sfl.safetysecurity.intrusion.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Additive S162 role -> permission mapping, following {@code AccessControlPermissionMatrix}'s shape. */
public final class IntrusionPermissionMatrix {

    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();

    private IntrusionPermissionMatrix() {
    }

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole, Set<SflPermission>> m = new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all = EnumSet.noneOf(SflPermission.class);
        for (var p : SflPermission.values()) {
            if (p.name().startsWith("INTRUSION_")) {
                all.add(p);
            }
        }
        m.put(SflRole.SFL_ADMIN, all);
        m.put(SflRole.SECURITY_DIRECTOR, EnumSet.copyOf(all));

        // SOC Operator (S162-02/03/04 user stories): the day-to-day alarm queue - acknowledge, resolve,
        // link evidence/incident, request disarms, read zone/panel health. Not zone definition itself.
        m.put(SflRole.SOC_OPERATOR, EnumSet.of(SflPermission.INTRUSION_ALARM_READ,
                SflPermission.INTRUSION_ALARM_ACKNOWLEDGE, SflPermission.INTRUSION_ALARM_RESOLVE,
                SflPermission.INTRUSION_ALARM_LINK_EVIDENCE, SflPermission.INTRUSION_ALARM_LINK_INCIDENT,
                SflPermission.INTRUSION_ZONE_READ, SflPermission.INTRUSION_ZONE_DISARM,
                SflPermission.INTRUSION_PANEL_HEALTH_READ, SflPermission.INTRUSION_DISPATCH_RECORD,
                SflPermission.INTRUSION_DISPATCH_READ));

        // Command Role (NECC - S162-02's escalation target for examination-affecting/critical alarms):
        // read-only visibility, no operational actions.
        m.put(SflRole.COMMAND_ROLE, EnumSet.of(SflPermission.INTRUSION_ALARM_READ,
                SflPermission.INTRUSION_ZONE_READ, SflPermission.INTRUSION_PANEL_HEALTH_READ));

        // Integration Engineer: reads what has been ingested; never acknowledges or manages zones.
        m.put(SflRole.INTEGRATION_ENGINEER, EnumSet.of(SflPermission.INTRUSION_ALARM_READ,
                SflPermission.INTRUSION_PANEL_HEALTH_READ, SflPermission.INTRUSION_ZONE_READ));

        m.put(SflRole.AUDITOR, EnumSet.of(SflPermission.INTRUSION_ALARM_READ, SflPermission.INTRUSION_ZONE_READ,
                SflPermission.INTRUSION_PANEL_HEALTH_READ, SflPermission.INTRUSION_DISPATCH_READ,
                SflPermission.INTRUSION_REPORT_READ));

        m.put(SflRole.COMPLIANCE_OFFICER, EnumSet.of(SflPermission.INTRUSION_ALARM_READ,
                SflPermission.INTRUSION_REPORT_READ));

        m.replaceAll((r, p) -> Set.copyOf(p));
        return Map.copyOf(m);
    }
}
