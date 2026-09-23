package gh.edu.clet.sfl.safetysecurity.lifesafety.application.port;

import java.util.Set;

/**
 * SRS-SFL-S162a-04: combines the on-site visitor population (S160) and access-derived occupancy
 * (S160a) into the muster/roll-call view. Phase-1 default is a no-op (empty roster) - the muster
 * mechanism itself (open a session, check people in, highlight who has not checked in yet) works
 * standalone against whatever population is returned here, so this is a documented follow-up to wire
 * a real adapter against Visitor/AccessControl, not a gap in the muster mechanism itself.
 */
public interface OnSitePopulationPort {

    /** Everyone who should be accounted for in this zone right now, by person reference. */
    Set<String> onSitePersons(String siteCode, String zoneCode);
}
