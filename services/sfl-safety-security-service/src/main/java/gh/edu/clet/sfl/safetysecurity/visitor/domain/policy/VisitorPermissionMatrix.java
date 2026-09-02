package gh.edu.clet.sfl.safetysecurity.visitor.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Additive S160 role -> permission mapping, following {@code EmergencyPermissionMatrix}'s shape. */
public final class VisitorPermissionMatrix {

    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();

    private VisitorPermissionMatrix() {
    }

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole, Set<SflPermission>> m = new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all = EnumSet.noneOf(SflPermission.class);
        for (var p : SflPermission.values()) {
            if (p.name().startsWith("VISITOR_")) {
                all.add(p);
            }
        }
        m.put(SflRole.SFL_ADMIN, all);
        m.put(SflRole.SECURITY_DIRECTOR, EnumSet.copyOf(all));

        // Reception: registers, badges, checks in/out. No approval, no watchlist override.
        m.put(SflRole.RECEPTION_OFFICER, EnumSet.of(SflPermission.VISITOR_VISIT_READ,
                SflPermission.VISITOR_VISIT_CREATE, SflPermission.VISITOR_BADGE_ASSIGN,
                SflPermission.VISITOR_CHECKIN, SflPermission.VISITOR_CHECKOUT, SflPermission.VISITOR_CANCEL,
                SflPermission.VISITOR_ROLLCALL_READ));

        // Host: reads and decides on visits requested of them, and can cancel their own.
        m.put(SflRole.VISITOR_HOST, EnumSet.of(SflPermission.VISITOR_VISIT_READ,
                SflPermission.VISITOR_VISIT_CREATE, SflPermission.VISITOR_VISIT_APPROVE,
                SflPermission.VISITOR_CANCEL));

        // SOC operator: roll-call visibility and read, for the security desk during an incident.
        m.put(SflRole.SOC_OPERATOR, EnumSet.of(SflPermission.VISITOR_VISIT_READ,
                SflPermission.VISITOR_ROLLCALL_READ, SflPermission.VISITOR_WATCHLIST_OVERRIDE,
                SflPermission.VISITOR_REPORT_READ));

        m.put(SflRole.AUDITOR, EnumSet.of(SflPermission.VISITOR_VISIT_READ, SflPermission.VISITOR_REPORT_READ));

        m.put(SflRole.COMPLIANCE_OFFICER, EnumSet.of(SflPermission.VISITOR_VISIT_READ,
                SflPermission.VISITOR_REPORT_READ));

        m.replaceAll((r, p) -> Set.copyOf(p));
        return Map.copyOf(m);
    }
}
