package gh.edu.clet.sfl.safetysecurity.cctv.domain.policy;

import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.common.security.SflRole;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Additive S161 role -> permission mapping, following {@code AccessControlPermissionMatrix}'s shape. */
public final class CctvPermissionMatrix {

    private static final Map<SflRole, Set<SflPermission>> MATRIX = build();

    private CctvPermissionMatrix() {
    }

    public static boolean grants(Set<SflRole> roles, SflPermission permission) {
        return roles != null && roles.stream().anyMatch(r -> MATRIX.getOrDefault(r, Set.of()).contains(permission));
    }

    private static Map<SflRole, Set<SflPermission>> build() {
        Map<SflRole, Set<SflPermission>> m = new EnumMap<>(SflRole.class);
        EnumSet<SflPermission> all = EnumSet.noneOf(SflPermission.class);
        for (var p : SflPermission.values()) {
            if (p.name().startsWith("CCTV_")) {
                all.add(p);
            }
        }
        m.put(SflRole.SFL_ADMIN, all);
        m.put(SflRole.SECURITY_DIRECTOR, EnumSet.copyOf(all));

        // SOC Operator (S161-01/04 user stories): camera health, live view, alert triage, evidence
        // requesting and reading what has been retrieved (retrieval itself logs an access, so this role
        // needs CCTV_EVIDENCE_ITEM_READ to carry out the retrieval it is also the one authorised to
        // perform) - but not approving a request, and not disclosure. "Footage is used lawfully and never
        // accessed casually" (S161-02's own Security Director user story) is exactly the separation of
        // duties this withholds CCTV_EVIDENCE_REQUEST_APPROVE for.
        m.put(SflRole.SOC_OPERATOR, EnumSet.of(SflPermission.CCTV_CAMERA_READ,
                SflPermission.CCTV_EVIDENCE_REQUEST_CREATE, SflPermission.CCTV_EVIDENCE_REQUEST_READ,
                SflPermission.CCTV_EVIDENCE_ITEM_READ, SflPermission.CCTV_LIVE_VIEW_START,
                SflPermission.CCTV_LIVE_VIEW_READ, SflPermission.CCTV_ANALYTICS_ALERT_READ,
                SflPermission.CCTV_ANALYTICS_ALERT_ACKNOWLEDGE));

        // Incident Investigator (S161-03 user story): reads evidence, its access log and case linkage;
        // can request footage for a case but never approves its own request or a disclosure.
        m.put(SflRole.INCIDENT_INVESTIGATOR, EnumSet.of(SflPermission.CCTV_CAMERA_READ,
                SflPermission.CCTV_EVIDENCE_REQUEST_CREATE, SflPermission.CCTV_EVIDENCE_REQUEST_READ,
                SflPermission.CCTV_EVIDENCE_ITEM_READ, SflPermission.CCTV_DISCLOSURE_CREATE,
                SflPermission.CCTV_DISCLOSURE_READ));

        // Compliance Officer stands in for the SRS's "Data Protection Officer" (S161-05 user story):
        // retention/legal-hold and disclosure governance, read-only elsewhere.
        m.put(SflRole.COMPLIANCE_OFFICER, EnumSet.of(SflPermission.CCTV_CAMERA_READ,
                SflPermission.CCTV_EVIDENCE_REQUEST_READ, SflPermission.CCTV_EVIDENCE_ITEM_READ,
                SflPermission.CCTV_DISCLOSURE_APPROVE, SflPermission.CCTV_DISCLOSURE_READ,
                SflPermission.CCTV_RETENTION_MANAGE));

        // Integration Engineer: reads what has been ingested; never approves a request or a disclosure.
        m.put(SflRole.INTEGRATION_ENGINEER, EnumSet.of(SflPermission.CCTV_CAMERA_READ,
                SflPermission.CCTV_ANALYTICS_ALERT_READ));

        m.put(SflRole.AUDITOR, EnumSet.of(SflPermission.CCTV_CAMERA_READ, SflPermission.CCTV_EVIDENCE_REQUEST_READ,
                SflPermission.CCTV_EVIDENCE_ITEM_READ, SflPermission.CCTV_ANALYTICS_ALERT_READ,
                SflPermission.CCTV_DISCLOSURE_READ));

        m.replaceAll((r, p) -> Set.copyOf(p));
        return Map.copyOf(m);
    }
}
