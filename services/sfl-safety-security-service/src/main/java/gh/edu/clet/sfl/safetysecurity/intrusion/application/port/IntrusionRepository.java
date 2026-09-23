package gh.edu.clet.sfl.safetysecurity.intrusion.application.port;

import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverride;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DisarmOverrideStatus;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.DispatchOutcome;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionAlarm;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionSignal;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.IntrusionZone;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.PanelHealth;
import gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.ResponseDispatch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The one port for every S162 aggregate, following {@code AccessControlRepository}'s "one port per
 * module, several aggregates" shape - S162's five requirement areas are one cohesive workflow, not
 * five unrelated slices.
 */
public interface IntrusionRepository {

    IntrusionSignal saveSignal(IntrusionSignal signal);

    Optional<IntrusionSignal> findSignalByExternalId(String source, String externalEventId);

    IntrusionAlarm saveAlarm(IntrusionAlarm alarm);

    Optional<IntrusionAlarm> findAlarm(UUID id);

    /** The open alarm this panel/zone/type combination is already coalescing into, if any. */
    Optional<IntrusionAlarm> findOpenAlarm(String siteCode, String panelId, String zoneCode,
            gh.edu.clet.sfl.safetysecurity.intrusion.domain.model.AlarmType alarmType);

    List<IntrusionAlarm> findAlarmsByStatus(String siteCode, AlarmStatus status);

    /** Every {@code RAISED} alarm across every site - the escalation sweep's feed. */
    List<IntrusionAlarm> findRaisedAlarms();

    long countByOutcome(String siteCode, DispatchOutcome outcome);

    PanelHealth savePanelHealth(PanelHealth health);

    Optional<PanelHealth> findPanelHealth(String siteCode, String panelId);

    IntrusionZone saveZone(IntrusionZone zone);

    Optional<IntrusionZone> findZone(UUID id);

    Optional<IntrusionZone> findZoneByCode(String siteCode, String zoneCode);

    List<IntrusionZone> findZonesBySite(String siteCode);

    DisarmOverride saveDisarmOverride(DisarmOverride override);

    Optional<DisarmOverride> findDisarmOverride(UUID id);

    List<DisarmOverride> findDisarmOverridesByStatus(String siteCode, DisarmOverrideStatus status);

    /** Every {@code ACTIVE} disarm override across every site - the expiry sweep's feed. */
    List<DisarmOverride> findActiveDisarmOverrides();

    ResponseDispatch saveDispatch(ResponseDispatch dispatch);

    Optional<ResponseDispatch> findDispatch(UUID id);

    Optional<ResponseDispatch> findDispatchByAlarm(UUID alarmId);
}
