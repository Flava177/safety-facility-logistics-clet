package gh.edu.clet.sfl.emergencynotification.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Additive S174 role -> permission mapping; can later be replaced by permission claims. */
public final class EmergencyPermissionMatrix {

    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();

    private EmergencyPermissionMatrix() {
    }

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole, Set<SflPermission>> m = new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all = EnumSet.noneOf(SflPermission.class);
        for (var p : SflPermission.values()) {
            if (p.name().startsWith("EMERGENCY_")) {
                all.add(p);
            }
        }
        m.put(SflRole.SFL_ADMIN, all);
        m.put(SflRole.SECURITY_DIRECTOR, EnumSet.copyOf(all));

        // Emergency coordinator: owns records + activation lifecycle + break-glass, but not approval/after-action.
        m.put(SflRole.EMERGENCY_COORDINATOR, EnumSet.of(SflPermission.EMERGENCY_TEMPLATE_READ,
                SflPermission.EMERGENCY_TEMPLATE_MANAGE, SflPermission.EMERGENCY_SCENARIO_READ,
                SflPermission.EMERGENCY_SCENARIO_MANAGE, SflPermission.EMERGENCY_AUDIENCE_READ,
                SflPermission.EMERGENCY_AUDIENCE_MANAGE, SflPermission.EMERGENCY_ACTIVATION_READ,
                SflPermission.EMERGENCY_ACTIVATION_CREATE, SflPermission.EMERGENCY_ACTIVATION_SEND,
                SflPermission.EMERGENCY_BREAK_GLASS_SEND, SflPermission.EMERGENCY_ALL_CLEAR_SEND,
                SflPermission.EMERGENCY_EVIDENCE_READ, SflPermission.EMERGENCY_REPORT_READ));

        // SOC operator: triage + break-glass + callback ingest, no approval.
        m.put(SflRole.SOC_OPERATOR, EnumSet.of(SflPermission.EMERGENCY_TEMPLATE_READ,
                SflPermission.EMERGENCY_SCENARIO_READ, SflPermission.EMERGENCY_AUDIENCE_READ,
                SflPermission.EMERGENCY_ACTIVATION_READ, SflPermission.EMERGENCY_ACTIVATION_CREATE,
                SflPermission.EMERGENCY_ACTIVATION_SEND, SflPermission.EMERGENCY_BREAK_GLASS_SEND,
                SflPermission.EMERGENCY_ALL_CLEAR_SEND, SflPermission.EMERGENCY_INTEGRATION_INGEST,
                SflPermission.EMERGENCY_REPORT_READ));

        // Security officer on the ground: create/send + break-glass.
        m.put(SflRole.SECURITY_OFFICER, EnumSet.of(SflPermission.EMERGENCY_ACTIVATION_READ,
                SflPermission.EMERGENCY_ACTIVATION_CREATE, SflPermission.EMERGENCY_ACTIVATION_SEND,
                SflPermission.EMERGENCY_BREAK_GLASS_SEND, SflPermission.EMERGENCY_ALL_CLEAR_SEND,
                SflPermission.EMERGENCY_REPORT_READ));

        // HSE manager: routine activation + approval, no break-glass.
        m.put(SflRole.HSE_MANAGER, EnumSet.of(SflPermission.EMERGENCY_TEMPLATE_READ,
                SflPermission.EMERGENCY_SCENARIO_READ, SflPermission.EMERGENCY_AUDIENCE_READ,
                SflPermission.EMERGENCY_ACTIVATION_READ, SflPermission.EMERGENCY_ACTIVATION_CREATE,
                SflPermission.EMERGENCY_ACTIVATION_APPROVE, SflPermission.EMERGENCY_ACTIVATION_SEND,
                SflPermission.EMERGENCY_ALL_CLEAR_SEND, SflPermission.EMERGENCY_REPORT_READ));

        // Command role: approval + after-action approval oversight.
        m.put(SflRole.COMMAND_ROLE, EnumSet.of(SflPermission.EMERGENCY_ACTIVATION_READ,
                SflPermission.EMERGENCY_ACTIVATION_APPROVE, SflPermission.EMERGENCY_AFTER_ACTION_APPROVE,
                SflPermission.EMERGENCY_ALL_CLEAR_SEND, SflPermission.EMERGENCY_REPORT_READ));

        m.put(SflRole.AUDITOR, EnumSet.of(SflPermission.EMERGENCY_ACTIVATION_READ,
                SflPermission.EMERGENCY_TEMPLATE_READ, SflPermission.EMERGENCY_SCENARIO_READ,
                SflPermission.EMERGENCY_AUDIENCE_READ, SflPermission.EMERGENCY_EVIDENCE_READ,
                SflPermission.EMERGENCY_EVIDENCE_EXPORT, SflPermission.EMERGENCY_REPORT_READ,
                SflPermission.EMERGENCY_REPORT_EXPORT));

        m.put(SflRole.COMPLIANCE_OFFICER, EnumSet.of(SflPermission.EMERGENCY_ACTIVATION_READ,
                SflPermission.EMERGENCY_ACTIVATION_APPROVE, SflPermission.EMERGENCY_AFTER_ACTION_APPROVE,
                SflPermission.EMERGENCY_EVIDENCE_READ, SflPermission.EMERGENCY_EVIDENCE_EXPORT,
                SflPermission.EMERGENCY_REPORT_READ, SflPermission.EMERGENCY_REPORT_EXPORT));

        m.put(SflRole.INTEGRATION_ENGINEER, EnumSet.of(SflPermission.EMERGENCY_ACTIVATION_READ,
                SflPermission.EMERGENCY_INTEGRATION_INGEST, SflPermission.EMERGENCY_INTEGRATION_REPLAY,
                SflPermission.EMERGENCY_REPORT_READ));

        m.replaceAll((r, p) -> Set.copyOf(p));
        return Map.copyOf(m);
    }
}
