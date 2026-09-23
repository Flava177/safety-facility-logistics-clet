package gh.edu.clet.sfl.safetysecurity.lifesafety.infrastructure.integration;

import gh.edu.clet.sfl.safetysecurity.emergency.application.port.IntegrationSeamPorts.LifeSafetyEventPort;
import gh.edu.clet.sfl.safetysecurity.lifesafety.application.port.LifeSafetyRepository;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Replaces {@code RecordedIntegrationSeams}'s empty stub for S174's OBSERVE-ONLY life-safety seam
 * (SRS-SFL-S162a-01/§0E) with the real thing, now that this module exists to observe something: the
 * latest event this module has actually ingested for the site. Still observe-only - it answers a
 * query, it issues no command - and it is the only other class besides {@code EmergencyFastLaneAdapter}
 * that imports anything from {@code emergency}.
 */
@Component
public class LifeSafetyEventPortAdapter implements LifeSafetyEventPort {

    private final LifeSafetyRepository repository;

    public LifeSafetyEventPortAdapter(LifeSafetyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> latestLifeSafetyEvent(String siteCode) {
        return repository.findLatestEvent(siteCode)
                .map(e -> e.kind() + " at zone " + e.zoneCode() + " (" + e.occurredAt() + ")");
    }
}
