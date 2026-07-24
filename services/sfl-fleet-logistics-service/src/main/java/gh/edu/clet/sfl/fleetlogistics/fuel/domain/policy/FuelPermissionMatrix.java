package gh.edu.clet.sfl.fleetlogistics.fuel.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Additive S168_fuel role mapping; it can later be replaced by permission claims. */
public final class FuelPermissionMatrix {
    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();
    private FuelPermissionMatrix() {}
    public static boolean grants(Set<SflRole> roles, SflPermission permission) { return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission)); }
    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole,Set<SflPermission>> m=new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all=EnumSet.noneOf(SflPermission.class); for(var p:SflPermission.values())if(p.name().startsWith("FUEL_"))all.add(p);
        m.put(SflRole.SFL_ADMIN,all); m.put(SflRole.FLEET_MANAGER,EnumSet.copyOf(all));
        m.put(SflRole.FLEET_LOGISTICS_OFFICER,EnumSet.of(SflPermission.FUEL_TRANSACTION_READ,SflPermission.FUEL_TRANSACTION_CAPTURE,SflPermission.FUEL_TRANSACTION_IMPORT,SflPermission.FUEL_POLICY_READ,SflPermission.FUEL_LOGBOOK_READ,SflPermission.FUEL_LOGBOOK_CREATE,SflPermission.FUEL_LOGBOOK_SUBMIT,SflPermission.FUEL_RECONCILIATION_RUN,SflPermission.FUEL_ANOMALY_READ,SflPermission.FUEL_ANOMALY_MANAGE,SflPermission.FUEL_REPORT_READ));
        m.put(SflRole.FLEET_DRIVER,EnumSet.of(SflPermission.FUEL_TRANSACTION_READ,SflPermission.FUEL_LOGBOOK_READ,SflPermission.FUEL_LOGBOOK_CREATE,SflPermission.FUEL_LOGBOOK_SUBMIT));
        m.put(SflRole.FLEET_REPORTING_VIEWER,EnumSet.of(SflPermission.FUEL_TRANSACTION_READ,SflPermission.FUEL_LOGBOOK_READ,SflPermission.FUEL_ANOMALY_READ,SflPermission.FUEL_REPORT_READ));
        m.put(SflRole.COMMAND_ROLE,EnumSet.of(SflPermission.FUEL_TRANSACTION_READ,SflPermission.FUEL_ANOMALY_READ,SflPermission.FUEL_REPORT_READ));
        m.put(SflRole.AUDITOR,EnumSet.of(SflPermission.FUEL_TRANSACTION_READ,SflPermission.FUEL_LOGBOOK_READ,SflPermission.FUEL_ANOMALY_READ,SflPermission.FUEL_REPORT_READ,SflPermission.FUEL_REPORT_EXPORT));
        m.put(SflRole.COMPLIANCE_OFFICER,EnumSet.of(SflPermission.FUEL_TRANSACTION_READ,SflPermission.FUEL_LOGBOOK_READ,SflPermission.FUEL_ANOMALY_READ,SflPermission.FUEL_ANOMALY_APPROVE,SflPermission.FUEL_REPORT_READ,SflPermission.FUEL_REPORT_EXPORT));
        m.put(SflRole.DTI_ADMIN,EnumSet.of(SflPermission.FUEL_TRANSACTION_READ,SflPermission.FUEL_POLICY_READ,SflPermission.FUEL_REPORT_READ,SflPermission.FUEL_INTEGRATION_REPLAY));
        m.put(SflRole.INTEGRATION_ENGINEER,EnumSet.of(SflPermission.FUEL_TRANSACTION_IMPORT,SflPermission.FUEL_INTEGRATION_INGEST,SflPermission.FUEL_INTEGRATION_REPLAY));
        m.put(SflRole.SERVICE_INTEGRATION,EnumSet.of(SflPermission.FUEL_INTEGRATION_INGEST));
        m.replaceAll((r,p)->Set.copyOf(p)); return Map.copyOf(m);
    }
}
