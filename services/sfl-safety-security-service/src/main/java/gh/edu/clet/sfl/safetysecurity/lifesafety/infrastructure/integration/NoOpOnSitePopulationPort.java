package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.integration;

import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.OnSitePopulationPort;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Phase-1 default for {@link OnSitePopulationPort}: no known roster. See the port's javadoc - the
 * muster mechanism works standalone against whatever this returns, and combining Visitor (S160) and
 * Access Control (S160a) occupancy into a real roster is a documented follow-up, matching how S160a
 * itself deprioritised the equivalent combination.
 */
@Component
public class NoOpOnSitePopulationPort implements OnSitePopulationPort {

    @Override
    public Set<String> onSitePersons(String siteCode, String zoneCode) {
        return Set.of();
    }
}
