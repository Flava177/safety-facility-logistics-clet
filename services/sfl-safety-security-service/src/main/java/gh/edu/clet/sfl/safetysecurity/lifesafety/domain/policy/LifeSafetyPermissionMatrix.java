package gh.edu.clet.sfl.safetysecurity.lifesafety.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Additive S162a role -> permission mapping, following {@code IncidentPermissionMatrix}'s shape. */
public final class LifeSafetyPermissionMatrix {

    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();

    private LifeSafetyPermissionMatrix() {
    }

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole, Set<SflPermission>> m = new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all = EnumSet.noneOf(SflPermission.class);
        for (var p : SflPermission.values()) {
            if (p.name().startsWith("LIFESAFETY_")) {
                all.add(p);
            }
        }
        m.put(SflRole.SFL_ADMIN, all);
        m.put(SflRole.SECURITY_DIRECTOR, EnumSet.copyOf(all));

        // Safety Officer user story (S162a-01/03/05): HSE_MANAGER already carries the S163 safety
        // remit - inspections, compliance exceptions and coverage are theirs to manage.
        m.put(SflRole.HSE_MANAGER, EnumSet.of(SflPermission.LIFESAFETY_EVENT_READ,
                SflPermission.LIFESAFETY_INSPECTION_MANAGE, SflPermission.LIFESAFETY_INSPECTION_READ,
                SflPermission.LIFESAFETY_COMPLIANCE_EXCEPTION_READ, SflPermission.LIFESAFETY_COMPLIANCE_EXCEPTION_RESOLVE,
                SflPermission.LIFESAFETY_COVERAGE_MANAGE, SflPermission.LIFESAFETY_COVERAGE_READ,
                SflPermission.LIFESAFETY_FASTLANE_READ));

        // Facilities/Safety Officer follow-up (S162a-03): sees inspections and compliance exceptions
        // that will land a maintenance work order in IFIMP, but does not run the SOC/muster side.
        m.put(SflRole.FACILITIES_MANAGER, EnumSet.of(SflPermission.LIFESAFETY_INSPECTION_READ,
                SflPermission.LIFESAFETY_COMPLIANCE_EXCEPTION_READ, SflPermission.LIFESAFETY_COVERAGE_READ));

        // SOC Operator / Command Role (S162a-01/02): observe events and the fast-lane trail, run muster.
        m.put(SflRole.SOC_OPERATOR, EnumSet.of(SflPermission.LIFESAFETY_EVENT_READ,
                SflPermission.LIFESAFETY_FASTLANE_READ, SflPermission.LIFESAFETY_MUSTER_READ,
                SflPermission.LIFESAFETY_MUSTER_CHECKIN));
        m.put(SflRole.COMMAND_ROLE, EnumSet.of(SflPermission.LIFESAFETY_EVENT_READ,
                SflPermission.LIFESAFETY_FASTLANE_READ, SflPermission.LIFESAFETY_MUSTER_READ));

        // Emergency Coordinator (S162a-04): roll-call and muster check-in during an evacuation.
        m.put(SflRole.EMERGENCY_COORDINATOR, EnumSet.of(SflPermission.LIFESAFETY_MUSTER_READ,
                SflPermission.LIFESAFETY_MUSTER_CHECKIN, SflPermission.LIFESAFETY_EVENT_READ));

        // Integration Engineer: reads what has been ingested; manages nothing.
        m.put(SflRole.INTEGRATION_ENGINEER, EnumSet.of(SflPermission.LIFESAFETY_EVENT_READ,
                SflPermission.LIFESAFETY_FASTLANE_READ));

        // Compliance Officer (merged with the former AUDITOR role - this module is observe-only, so
        // there was never an approval action to distinguish them here; the two were one role wearing
        // two names).
        m.put(SflRole.COMPLIANCE_OFFICER, EnumSet.of(SflPermission.LIFESAFETY_EVENT_READ,
                SflPermission.LIFESAFETY_FASTLANE_READ, SflPermission.LIFESAFETY_INSPECTION_READ,
                SflPermission.LIFESAFETY_COMPLIANCE_EXCEPTION_READ, SflPermission.LIFESAFETY_COVERAGE_READ,
                SflPermission.LIFESAFETY_MUSTER_READ));

        m.replaceAll((r, p) -> Set.copyOf(p));
        return Map.copyOf(m);
    }
}
