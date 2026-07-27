package gh.edu.clet.sfl.fleetlogistics.dispatch.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Additive S171 role -> permission mapping; it can later be replaced by permission claims. */
public final class DispatchPermissionMatrix {
    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();
    private DispatchPermissionMatrix() {}

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole, Set<SflPermission>> m = new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all = EnumSet.noneOf(SflPermission.class);
        for (var p : SflPermission.values()) if (p.name().startsWith("DISPATCH_")) all.add(p);
        m.put(SflRole.SFL_ADMIN, all);
        m.put(SflRole.FLEET_MANAGER, EnumSet.copyOf(all));
        EnumSet<SflPermission> controller = EnumSet.of(SflPermission.DISPATCH_ITEM_READ,
                SflPermission.DISPATCH_ITEM_REGISTER, SflPermission.DISPATCH_ITEM_MANAGE,
                SflPermission.DISPATCH_MANIFEST_READ, SflPermission.DISPATCH_MANIFEST_CREATE,
                SflPermission.DISPATCH_CUSTODY_RECORD, SflPermission.DISPATCH_RETURN_RECONCILE,
                SflPermission.DISPATCH_INBOUND_REGISTER, SflPermission.DISPATCH_EXCEPTION_READ,
                SflPermission.DISPATCH_EXCEPTION_MANAGE, SflPermission.DISPATCH_REPORT_READ);
        m.put(SflRole.DISPATCH_CONTROLLER, EnumSet.copyOf(controller));
        m.put(SflRole.LOGISTICS_COORDINATOR, EnumSet.copyOf(controller));
        m.put(SflRole.FLEET_LOGISTICS_OFFICER, EnumSet.copyOf(controller));
        m.put(SflRole.CENTRE_MANAGER, EnumSet.of(SflPermission.DISPATCH_ITEM_READ, SflPermission.DISPATCH_MANIFEST_READ,
                SflPermission.DISPATCH_CUSTODY_RECORD, SflPermission.DISPATCH_RECEIPT_CONFIRM,
                SflPermission.DISPATCH_EXCEPTION_READ, SflPermission.DISPATCH_REPORT_READ));
        m.put(SflRole.MAILROOM_OFFICER, EnumSet.of(SflPermission.DISPATCH_ITEM_READ, SflPermission.DISPATCH_ITEM_REGISTER,
                SflPermission.DISPATCH_ITEM_MANAGE, SflPermission.DISPATCH_INBOUND_REGISTER,
                SflPermission.DISPATCH_INBOUND_DISTRIBUTE, SflPermission.DISPATCH_MANIFEST_READ,
                SflPermission.DISPATCH_EXCEPTION_READ));
        m.put(SflRole.SECURITY_OFFICER, EnumSet.of(SflPermission.DISPATCH_ITEM_READ, SflPermission.DISPATCH_MANIFEST_READ,
                SflPermission.DISPATCH_EXCEPTION_READ, SflPermission.DISPATCH_EXCEPTION_ESCALATE,
                SflPermission.DISPATCH_REPORT_READ));
        m.put(SflRole.AUDITOR, EnumSet.of(SflPermission.DISPATCH_ITEM_READ, SflPermission.DISPATCH_MANIFEST_READ,
                SflPermission.DISPATCH_EXCEPTION_READ, SflPermission.DISPATCH_REPORT_READ,
                SflPermission.DISPATCH_REPORT_EXPORT));
        m.put(SflRole.COMPLIANCE_OFFICER, EnumSet.of(SflPermission.DISPATCH_ITEM_READ, SflPermission.DISPATCH_MANIFEST_READ,
                SflPermission.DISPATCH_EXCEPTION_READ, SflPermission.DISPATCH_EXCEPTION_APPROVE,
                SflPermission.DISPATCH_REPORT_READ, SflPermission.DISPATCH_REPORT_EXPORT));
        m.put(SflRole.FLEET_REPORTING_VIEWER, EnumSet.of(SflPermission.DISPATCH_ITEM_READ,
                SflPermission.DISPATCH_MANIFEST_READ, SflPermission.DISPATCH_EXCEPTION_READ,
                SflPermission.DISPATCH_REPORT_READ));
        m.put(SflRole.COMMAND_ROLE, EnumSet.of(SflPermission.DISPATCH_ITEM_READ, SflPermission.DISPATCH_MANIFEST_READ,
                SflPermission.DISPATCH_EXCEPTION_READ, SflPermission.DISPATCH_EXCEPTION_ESCALATE,
                SflPermission.DISPATCH_REPORT_READ));
        m.put(SflRole.DTI_ADMIN, EnumSet.of(SflPermission.DISPATCH_ITEM_READ, SflPermission.DISPATCH_MANIFEST_READ,
                SflPermission.DISPATCH_REPORT_READ, SflPermission.DISPATCH_INTEGRATION_REPLAY));
        m.put(SflRole.INTEGRATION_ENGINEER, EnumSet.of(SflPermission.DISPATCH_INTEGRATION_INGEST,
                SflPermission.DISPATCH_INTEGRATION_REPLAY));
        m.put(SflRole.SERVICE_INTEGRATION, EnumSet.of(SflPermission.DISPATCH_INTEGRATION_INGEST));
        m.replaceAll((r, p) -> Set.copyOf(p));
        return Map.copyOf(m);
    }
}
