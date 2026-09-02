package gh.edu.clet.sfl.safetysecurity.incident.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Additive S163 role -> permission mapping, following {@code EmergencyPermissionMatrix}'s and {@code
 * VisitorPermissionMatrix}'s shape.
 *
 * <p>D.9's "Staff/student/inspector" reporter is broader than any role this platform models today -
 * there is no "any authenticated CLET member" concept yet, so {@code INCIDENT_REPORT_CREATE} is
 * granted to every role plausibly already at CLET rather than invented as a new one; revisit once the
 * platform has a real base-staff role.
 */
public final class IncidentPermissionMatrix {

    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();

    private IncidentPermissionMatrix() {
    }

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole, Set<SflPermission>> m = new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all = EnumSet.noneOf(SflPermission.class);
        for (var p : SflPermission.values()) {
            if (p.name().startsWith("INCIDENT_")) {
                all.add(p);
            }
        }
        m.put(SflRole.SFL_ADMIN, all);
        m.put(SflRole.SECURITY_DIRECTOR, EnumSet.copyOf(all));

        // HSE Officer persona (D.9 steps 2, 5): triages, preserves evidence, reports. Not investigation
        // or CAPA ownership - those are the Investigator's (D.9 steps 3-4).
        m.put(SflRole.HSE_MANAGER, EnumSet.of(SflPermission.INCIDENT_REPORT_CREATE,
                SflPermission.INCIDENT_REPORT_READ, SflPermission.INCIDENT_TRIAGE,
                SflPermission.INCIDENT_EVIDENCE_MANAGE, SflPermission.INCIDENT_REPORT_EXPORT));

        // Investigator persona (D.9 steps 3-4): opens investigations, owns CAPA, closes cases.
        m.put(SflRole.INCIDENT_INVESTIGATOR, EnumSet.of(SflPermission.INCIDENT_REPORT_READ,
                SflPermission.INCIDENT_INVESTIGATE, SflPermission.INCIDENT_EVIDENCE_MANAGE,
                SflPermission.INCIDENT_CAPA_MANAGE, SflPermission.INCIDENT_CAPA_VERIFY,
                SflPermission.INCIDENT_CLOSE));

        // SOC operator: front-line reporting, same as reception can pre-register a visit.
        m.put(SflRole.SOC_OPERATOR, EnumSet.of(SflPermission.INCIDENT_REPORT_CREATE,
                SflPermission.INCIDENT_REPORT_READ));

        // Compliance Officer (D.9 step 8): dashboards and statutory/management exports, not the case
        // workflow itself. Already exists as a role (added for S168/fleet compliance reporting).
        m.put(SflRole.COMPLIANCE_OFFICER, EnumSet.of(SflPermission.INCIDENT_REPORT_READ,
                SflPermission.INCIDENT_REPORT_EXPORT));

        m.put(SflRole.AUDITOR, EnumSet.of(SflPermission.INCIDENT_REPORT_READ, SflPermission.INCIDENT_REPORT_EXPORT));

        m.replaceAll((r, p) -> Set.copyOf(p));
        return Map.copyOf(m);
    }
}
